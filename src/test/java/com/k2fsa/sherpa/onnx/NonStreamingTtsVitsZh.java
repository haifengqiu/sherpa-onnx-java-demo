package com.k2fsa.sherpa.onnx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.File;

public class NonStreamingTtsVitsZh {
  private static final Logger log = LoggerFactory.getLogger(NonStreamingTtsVitsZh.class);

  public static void main(String[] args) {
    // please visit
    // https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
    // to download model files
    String model = "./sherpa-onnx/tts/model.onnx";
    String tokens = "./sherpa-onnx/tts/tokens.txt";
    String lexicon = "./sherpa-onnx/tts/lexicon.txt";
//    String dictDir = "./sherpa-onnx/tts/dict";
    String ruleFsts =
      "./sherpa-onnx/tts/phone.fst,./sherpa-onnx/tts/date.fst,./sherpa-onnx/tts/number.fst";
    String text = "请说：打开第一个摄像头！";

    OfflineTtsVitsModelConfig vitsModelConfig =
      OfflineTtsVitsModelConfig.builder()
        .setModel(model)
        .setTokens(tokens)
        .setLexicon(lexicon)
        .build();

    OfflineTtsModelConfig modelConfig =
      OfflineTtsModelConfig.builder()
        .setVits(vitsModelConfig)
        .setNumThreads(1)
        .setDebug(true)
        .build();

    OfflineTtsConfig config =
      OfflineTtsConfig.builder().setModel(modelConfig).setRuleFsts(ruleFsts).build();

    OfflineTts tts = new OfflineTts(config);

    // 1 4 5 6 9 13 16后面没看
    int sid = 8;
    float speed = 1.0f;
    long start = System.currentTimeMillis();
    log.info("开始TTS: " + text);
    GeneratedAudio audio = tts.generate(text, sid, speed);
    log.info("结束TTS: " + audio.getSamples().length);
    long stop = System.currentTimeMillis();

    float timeElapsedSeconds = (stop - start) / 1000.0f;

    float audioDuration = audio.getSamples().length / (float) audio.getSampleRate();
    float real_time_factor = timeElapsedSeconds / audioDuration;

    String waveFilename = "temp/tts-vits-zh.wav";
    audio.save(waveFilename);
    System.out.printf("-- elapsed : %.3f seconds\n", timeElapsedSeconds);
    System.out.printf("-- audio duration: %.3f seconds\n", timeElapsedSeconds);
    System.out.printf("-- real-time factor (RTF): %.3f\n", real_time_factor);
    System.out.printf("-- text: %s\n", text);
    System.out.printf("-- Saved to %s\n", waveFilename);

    tts.release();
    log.info("开始播放");
    play(waveFilename);
    log.info("结束播放");
  }

  public static void play(String audioFilePath) {
    // 创建一个新的Clip对象
    Clip clip = null;
    try {
      // 获取音频文件的输入流
      AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(audioFilePath));
      // https://docs.oracle.com/javase/8/docs/api/javax/sound/sampled/AudioFormat.html
      // Linear PCM, 16000Hz, 16-bit, 1 channel, signed, little endian
      // https://docs.oracle.com/javase/8/docs/api/javax/sound/sampled/DataLine.Info.html#Info-java.lang.Class-javax.sound.sampled.AudioFormat-int-
      // 获取默认混音器
      Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
      Mixer mixer = AudioSystem.getMixer(mixerInfos[0]);

      // 获取音频格式
      AudioFormat format = audioInputStream.getFormat();

      // 输出音频格式信息
      System.out.println("Sample Rate: " + format.getSampleRate());
      System.out.println("Sample Size in Bits: " + format.getSampleSizeInBits());
      System.out.println("Channels: " + format.getChannels());
      System.out.println("Frame Rate: " + format.getFrameRate());
      System.out.println("Frame Size: " + format.getFrameSize());
      System.out.println("Encoding: " + format.getEncoding());

      // 创建 DataLine.Info 对象
      DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

      if (!AudioSystem.isLineSupported(info)) {
        System.out.println("Line not supported");
        return;
      }

      // 打开音频线
      SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
      line.open(format);
      line.start();

      byte[] buffer = new byte[4096];
      int bytesRead = 0;

      while ((bytesRead = audioInputStream.read(buffer)) != -1) {
        line.write(buffer, 0, bytesRead);
      }

      line.drain();
      line.close();
      audioInputStream.close();


//      // 打开Clip
//      clip = AudioSystem.getClip();
//      clip.open(audioInputStream);
//
//      // 设置音量（如果需要）
//      FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
//      gainControl.setValue(10.0f); // 增加音量，减少负值
//
//      // 开始播放
//      log.info("开始播放: " + audioFilePath);
//      clip.start();
//
//      // 等待音频播放完成
//      clip.drain();
//      log.info("结束播放: " + audioFilePath);
//      // 关闭Clip
//      clip.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
