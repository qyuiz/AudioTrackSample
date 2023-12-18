package com.example.audiotracksample;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AudioTrackSample";

    // hello_world.wav のサンプリングレート
    private static final int SamplingRate = 32000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ボタンを設定
        Button button = findViewById(R.id.play_button);

        // リスナーをボタンに登録
        // expression lambda
        button.setOnClickListener(v-> wavPlay());
    }

    private void wavPlay() {
        InputStream input = null;
        byte[] wavData = null;

        try {
            // wavを読み込む
            input = getResources().openRawResource(R.raw.how_are_you);
            wavData = new byte[input.available()]; // make wavData array
            Log.d(TAG, "wavData available byte size = " + wavData.length);

            // input.read(wavData)
            int byteSize = input.read(wavData);
            String readBytes = String.format(
                    Locale.US, "wavData read byte size = %d", byteSize);
            Log.d(TAG, readBytes);
            input.close();

        } catch (FileNotFoundException fne) {
            fne.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            Log.d(TAG, "error:" + ioe.getMessage());
        } finally{
            try{
                if(input != null) input.close();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        // read wavData output
        Log.d(TAG, "wavData byte = " + Arrays.toString(wavData));

        // バッファサイズの計算
        int bufSize = android.media.AudioTrack.getMinBufferSize(
                SamplingRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        Log.d(TAG, "bufSize = " + bufSize);

        // http://bril-tech.blogspot.com/2015/08/androidaudiotrack.html
        AudioTrack audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SamplingRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufSize)
                .build();

        // 再生
        audioTrack.play();

        // ヘッダ44byteをオミット
        if (wavData != null){
            // audioTrack.write(wavData, 0, wavData.length);
            audioTrack.write(wavData, 44, wavData.length-44);
        }
        audioTrack.stop();
        audioTrack.release();
    }
}