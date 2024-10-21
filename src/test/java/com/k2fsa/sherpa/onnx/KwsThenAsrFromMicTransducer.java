package com.k2fsa.sherpa.onnx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;

public class KwsThenAsrFromMicTransducer {
  private static final Logger log = LoggerFactory.getLogger(KwsThenAsrFromMicTransducer.class);
  static KeywordSpotter kws;
  static OnlineRecognizer recognizer;

  private static void initKws(String modelPath) {
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

    kws = new KeywordSpotter(config);
    kwsStream = kws.createStream();
  }

  private static void initAsr(String modelPath) {
    String encoder =
      "./sherpa-onnx/asr/encoder-epoch-99-avg-1.int8.onnx";
    String decoder =
      "./sherpa-onnx/asr/decoder-epoch-99-avg-1.onnx";
    String joiner =
      "./sherpa-onnx/asr/joiner-epoch-99-avg-1.onnx";
    String tokens = "./sherpa-onnx/asr/tokens.txt";

    // https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/itn_zh_number.fst
    String ruleFsts = "./sherpa-onnx/asr/itn_zh_number.fst";

    float hotwordsScore = 2.0f;
    String hotwordsFile =
      "./sherpa-onnx/asr/hotwords.txt";
    String bpeFile = "./sherpa-onnx/asr/bpe.vocab";

    int sampleRate = 16000;

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
        .setModelingUnit("cjkchar+bpe")
        .setBpeVocab(bpeFile)
        .build();

    OnlineRecognizerConfig config =
      OnlineRecognizerConfig.builder()
        .setOnlineModelConfig(modelConfig)
//        .setDecodingMethod("greedy_search")
        .setDecodingMethod("modified_beam_search")
        .setHotwordsFile(hotwordsFile)
        .setHotwordsScore(hotwordsScore)
        .setRuleFsts(ruleFsts)
        .build();

    recognizer = new OnlineRecognizer(config);
    asrStream = recognizer.createStream();
  }

  static volatile boolean isKws = true;
  static final int sampleRate = 16000;

  static OnlineStream kwsStream;

  private static void inKwsState(float[] samples) {
    kwsStream.acceptWaveform(samples, sampleRate);
    while (kws.isReady(kwsStream)) {
      kws.decode(kwsStream);
    }

    String keyword = kws.getResult(kwsStream).getKeyword();
    if (!keyword.isEmpty()) {
      log.info("检测到唤醒词: {}", keyword);
      // 切换到asr模式
      log.info("------------进入语音识别模式------------");
      isKws = false;
    }

  }

  static volatile String lastText = "";
  static volatile int segmentIndex = 0;

  static OnlineStream asrStream;

  private static String inAsrState(float[] samples) {
    asrStream.acceptWaveform(samples, sampleRate);
    while (recognizer.isReady(asrStream)) {
      recognizer.decode(asrStream);
    }

    while (recognizer.isReady(asrStream)) {
      recognizer.decode(asrStream);
    }
    String text = recognizer.getResult(asrStream).getText();
    boolean isEndpoint = recognizer.isEndpoint(asrStream);
    if (!text.isEmpty() && text != " " && !lastText.equals(text)) {
      lastText = text;

      log.info("{}:{},lastText:{}", segmentIndex, text, lastText);
      if (text.equals("退出")) {
        log.info("------------退出语音识别模式，重新进入语音唤醒模式------------");
        recognizer.reset(asrStream);
        isKws = true;
      }
    }

    if (isEndpoint) {
      if (!text.isEmpty()) {
        log.info("------------isEndpoint true时才增加segmentIndex,当前为:{}:{}------------", segmentIndex, text);
        segmentIndex += 1;
      }

      recognizer.reset(asrStream);
    }

    return text;
  }

  public static void main(String[] args) {
    initKws(null);
    initAsr(null);
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
      recognizer.release();
      kws.release();
      return;
    }


    // You can choose an arbitrary number
    int bufferSize = 1600; // 0.1 seconds for 16000Hz
    byte[] buffer = new byte[bufferSize * 2]; // a short has 2 bytes
    float[] samples = new float[bufferSize];

    System.out.println("Started! Please speak");
    System.out.println("------------当前为语音唤醒模式------------");
    while (targetDataLine.isOpen()) {
      int n = targetDataLine.read(buffer, 0, buffer.length);
      if (n <= 0) {
        log.info("Got %d bytes. Expected %d bytes.\n", n, buffer.length);
        continue;
      }
      for (int i = 0; i != bufferSize; ++i) {
        short low = buffer[2 * i];
        short high = buffer[2 * i + 1];
        int s = (high << 8) + low;
        samples[i] = (float) s / 32768;
      }

      if (isKws) {
        inKwsState(samples);
      } else {
        inAsrState(samples);
      }
    } // while (targetDataLine.isOpen())
  }
}
