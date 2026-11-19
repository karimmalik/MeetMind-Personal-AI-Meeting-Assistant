using System;
using System.Collections.Generic;
using System.IO;
using System.Threading;
using NAudio.CoreAudioApi;
using NAudio.Wave;
using NAudio.Wave.SampleProviders;

namespace RIMeet.NativeAudio
{
    internal static class LoopbackRecorder
    {
        private const int ChunkSeconds = 15;

        public static int Main(string[] args)
        {
            var outputDirectory = ReadOutputDirectory(args);
            Directory.CreateDirectory(outputDirectory);

            try
            {
                Record(outputDirectory);
                return 0;
            }
            catch (Exception error)
            {
                Console.Error.WriteLine(error.Message);
                Console.Out.WriteLine("ERROR\t" + error.Message);
                Console.Out.Flush();
                return 1;
            }
        }

        private static string ReadOutputDirectory(string[] args)
        {
            for (var index = 0; index < args.Length - 1; index++)
            {
                if (string.Equals(args[index], "--output-dir", StringComparison.OrdinalIgnoreCase)) return args[index + 1];
            }
            throw new ArgumentException("Missing --output-dir.");
        }

        private static void Record(string outputDirectory)
        {
            using (var devices = new MMDeviceEnumerator())
            using (var systemCapture = new WasapiLoopbackCapture(devices.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia)))
            using (var microphoneCapture = CreateMicrophoneCapture(devices))
            {
                var systemBuffer = CreateBuffer(systemCapture.WaveFormat);
                var microphoneBuffer = CreateBuffer(microphoneCapture.WaveFormat);
                systemCapture.DataAvailable += (sender, eventArgs) => systemBuffer.AddSamples(eventArgs.Buffer, 0, eventArgs.BytesRecorded);
                microphoneCapture.DataAvailable += (sender, eventArgs) => microphoneBuffer.AddSamples(eventArgs.Buffer, 0, eventArgs.BytesRecorded);

                var mixed = CreateMixer(systemBuffer, microphoneBuffer);
                var output = new SampleToWaveProvider16(mixed);
                var finalPath = Path.Combine(outputDirectory, "meeting.wav");
                var chunkNumber = 0;
                var chunkPath = NextChunkPath(outputDirectory, chunkNumber);

                using (var finalWriter = new WaveFileWriter(finalPath, output.WaveFormat))
                {
                    var chunkWriter = new RollingWaveWriter(chunkPath, output.WaveFormat);
                    try
                    {
                        systemCapture.StartRecording();
                        microphoneCapture.StartRecording();
                        var stopSignal = Path.Combine(outputDirectory, "stop.signal");
                        var frameBuffer = new byte[Math.Max(output.WaveFormat.AverageBytesPerSecond / 10, output.WaveFormat.BlockAlign)];
                        var chunkStarted = DateTime.UtcNow;

                        while (!File.Exists(stopSignal))
                        {
                            var count = output.Read(frameBuffer, 0, frameBuffer.Length);
                            if (count == 0)
                            {
                                Array.Clear(frameBuffer, 0, frameBuffer.Length);
                                count = frameBuffer.Length;
                            }
                            finalWriter.Write(frameBuffer, 0, count);
                            chunkWriter.Writer.Write(frameBuffer, 0, count);

                            if ((DateTime.UtcNow - chunkStarted).TotalSeconds >= ChunkSeconds)
                            {
                                chunkWriter.Close();
                                Console.Out.WriteLine("CHUNK\t" + chunkWriter.Path);
                                Console.Out.Flush();
                                chunkNumber++;
                                chunkPath = NextChunkPath(outputDirectory, chunkNumber);
                                chunkWriter = new RollingWaveWriter(chunkPath, output.WaveFormat);
                                chunkStarted = DateTime.UtcNow;
                            }
                            Thread.Sleep(100);
                        }

                        systemCapture.StopRecording();
                        microphoneCapture.StopRecording();
                        chunkWriter.Close();
                        if (new FileInfo(chunkWriter.Path).Length > 44)
                        {
                            Console.Out.WriteLine("CHUNK\t" + chunkWriter.Path);
                        }
                        Console.Out.WriteLine("FINAL\t" + finalPath);
                        Console.Out.Flush();
                    }
                    finally
                    {
                        chunkWriter.Close();
                    }
                }
            }
        }

        private static WasapiCapture CreateMicrophoneCapture(MMDeviceEnumerator devices)
        {
            try { return new WasapiCapture(devices.GetDefaultAudioEndpoint(DataFlow.Capture, Role.Communications)); }
            catch { return new WasapiCapture(devices.GetDefaultAudioEndpoint(DataFlow.Capture, Role.Multimedia)); }
        }

        private static BufferedWaveProvider CreateBuffer(WaveFormat format)
        {
            return new BufferedWaveProvider(format) { BufferDuration = TimeSpan.FromSeconds(3), DiscardOnBufferOverflow = true, ReadFully = true };
        }

        private static ISampleProvider CreateMixer(BufferedWaveProvider systemBuffer, BufferedWaveProvider microphoneBuffer)
        {
            var system = systemBuffer.ToSampleProvider();
            var microphone = microphoneBuffer.ToSampleProvider();
            if (microphone.WaveFormat.SampleRate != system.WaveFormat.SampleRate)
            {
                microphone = new WdlResamplingSampleProvider(microphone, system.WaveFormat.SampleRate);
            }
            if (microphone.WaveFormat.Channels == 1 && system.WaveFormat.Channels == 2)
            {
                microphone = new MonoToStereoSampleProvider(microphone);
            }
            if (microphone.WaveFormat.Channels != system.WaveFormat.Channels)
            {
                throw new InvalidOperationException("Microphone channel layout is not supported.");
            }
            return new MixingSampleProvider(new List<ISampleProvider> { system, microphone }) { ReadFully = true };
        }

        private static string NextChunkPath(string outputDirectory, int number)
        {
            return Path.Combine(outputDirectory, string.Format("chunk-{0:D5}.wav", number));
        }

        private sealed class RollingWaveWriter : IDisposable
        {
            public string Path { get; private set; }
            public WaveFileWriter Writer { get; private set; }

            public RollingWaveWriter(string path, WaveFormat format)
            {
                Path = path;
                Writer = new WaveFileWriter(path, format);
            }

            public void Close()
            {
                if (Writer == null) return;
                Writer.Dispose();
                Writer = null;
            }

            public void Dispose() { Close(); }
        }
    }
}
