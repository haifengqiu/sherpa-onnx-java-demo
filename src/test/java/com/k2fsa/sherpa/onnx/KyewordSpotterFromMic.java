package com.k2fsa.sherpa.onnx;

import javax.sound.sampled.*;

public class KyewordSpotterFromMic {
  public static void main(String[] args) {
    System.out.printf(System.getProperty("file.encoding"));
    // please download test files from https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models
    String encoder =
      "./sherpa-onnx/kws/encoder-epoch-12-avg-2-chunk-16-left-64.onnx";
    String decoder =
      "./sherpa-onnx/kws/decoder-epoch-12-avg-2-chunk-16-left-64.onnx";
    String joiner =
      "./sherpa-onnx/kws/joiner-epoch-12-avg-2-chunk-16-left-64.onnx";
    String tokens = "./sherpa-onnx/kws/tokens.txt";

    String keywordsFile =
      "./sherpa-onnx/kws/keywords.txt";

    OnlineTransducerModelConfig transducer =
      OnlineTransducerModelConfig.builder()
        .setEncoder(encoder)
        .setDecoder(decoder)
        .setJoiner(joiner)
        .build();

    OnlineModelConfig modelConfig =
      OnlineModelConfig.builder()
        .setTransducer(transducer)
        .setTokens(tokens)
        .setNumThreads(1)
        .setDebug(true)
        .build();

    KeywordSpotterConfig config =
      KeywordSpotterConfig.builder()
        .setOnlineModelConfig(modelConfig)
        .setKeywordsFile(keywordsFile)
        .build();

    KeywordSpotter kws = new KeywordSpotter(config);
    OnlineStream stream = kws.createStream();


    int sampleRate = 16000;
    // https://docs.oracle.com/javase/8/docs/api/javax/sound/sampled/AudioFormat.html
    // Linear PCM, 16000Hz, 16-bit, 1 channel, signed, little endian
    AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);

    // https://docs.oracle.com/javase/8/docs/api/javax/sound/sampled/DataLine.Info.html#Info-java.lang.Class-javax.sound.sampled.AudioFormat-int-
    DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
    TargetDataLine targetDataLine;
    try {
      targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
      targetDataLine.open(format);
      targetDataLine.start();
    } catch (LineUnavailableException e) {
      System.out.println("Failed to open target data line: " + e.getMessage());
      stream.release();
      kws.release();
      return;
    }

    String lastText = "";
    int segmentIndex = 0;

// You can choose an arbitrary number
    int bufferSize = 1600; // 0.1 seconds for 16000Hz
    byte[] buffer = new byte[bufferSize * 2]; // a short has 2 bytes
    float[] samples = new float[bufferSize];

    System.out.println("Started! Please speak");
    while (targetDataLine.isOpen()) {
      int n = targetDataLine.read(buffer, 0, buffer.length);
      if (n <= 0) {
        System.out.printf("Got %d bytes. Expected %d bytes.\n", n, buffer.length);
        continue;
      }
      for (int i = 0; i != bufferSize; ++i) {
        short low = buffer[2 * i];
        short high = buffer[2 * i + 1];
        int s = (high << 8) + low;
        samples[i] = (float) s / 32768;
      }


      stream.acceptWaveform(samples, sampleRate);

      while (kws.isReady(stream)) {
        kws.decode(stream);

        String keyword = kws.getResult(stream).getKeyword();
        if (!keyword.isEmpty()) {
          System.out.printf("中文Detected keyword: %s\n", keyword);
        }
      }
    } // while (targetDataLine.isOpen())

    stream.release();
    kws.release();
  }
}
