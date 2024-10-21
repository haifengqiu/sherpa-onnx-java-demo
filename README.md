# 概述
普通的maven工程，在mvn compile后，可以运行test下的测试类。注意如下几点即可：

1. -Djava.library.path=./sherpa-onnx/libs 添加到VM参数中，这是JNI调用所需。
2. ./sherpa-onnx/ 目录下有对应的模型文件，如果不是这个目录，修改对应test代码里的路径。
3. 不同操作系统下模型文件是一致的，但jni对应的/libs是不一样的。

更具体的介绍可见 [这篇博客](https://haifengqiu.github.io/blog/sherpa-java-quickstart) 。

## windows环境替换系统内置的onnxruntime.dll
如果是windows环境，请将系统盘里system32目录下的onnxruntime.dll替换为项目里sherpa-onnx/libs下的同名文件。



