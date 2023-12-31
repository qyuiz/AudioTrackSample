package com.example.audiotracksample;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.SoundPool;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AudioTrackSample";

    // hello_world.wav のサンプリングレート
    private int mSampleRate;
    private int mChannelConfig;
    private int mAudioFormat;
    private int mAllDataSize; // 全データサイズ
    private int mSoundDataSize; // 音声データサイズ
    private int mHeaderSize; // mAllDataSize - mSoundDataSize


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ボタンを設定
        Button button = findViewById(R.id.play_button);

        // リスナーをボタンに登録
        // expression lambda
        button.setOnClickListener(v-> {
            try {
                 wavPlay();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void wavPlay() throws Exception {

        try {
            int resourceId = R.raw.sine_400;
            // wavを読み込む
            InputStream input = getResources().openRawResource(resourceId);

            // ヘッダーを解析(データサイズと種類とスタート位置を取得)
            InputStream inputForHeader = getResources().openRawResource(resourceId);
            byte[] headerBuffer = new byte[inputForHeader.available()];
            mAllDataSize = inputForHeader.read(headerBuffer);
            checkWaveHeaderData(headerBuffer);

            // ヘッダーをスキップ(inputをヘッダー分、読み込んでスキップしてる)
            // データによっては、ヘッダが44byteとは限らない、、。
            // だが、必ず、fmt と data のチャンクIDは必ずある
            // data から取れる音声データのサイズ以外をヘッダとする
            headerBuffer = new byte[mHeaderSize];
            input.read(headerBuffer, 0, mHeaderSize);

            int bufferSize = AudioTrack.getMinBufferSize(mSampleRate, mChannelConfig, mAudioFormat);
            // http://bril-tech.blogspot.com/2015/08/androidaudiotrack.html
            AudioTrack audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(mSampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufferSize)
                    .build();
            float maxVolume = AudioTrack.getMaxVolume();
            Log.d(TAG, "maxVolume = " + maxVolume);
            float minVolume = AudioTrack.getMinVolume();
            Log.d(TAG, "minVolume = " + minVolume);

            // 読み込まれた分のサイズになっている
            Log.d(TAG, "input size : " + input.available());
            byte[] wavData = new byte[input.available()]; // wavDataのサイズで初期化

            // 再生準備開始
            audioTrack.play();

            int wavDataSize = input.read(wavData);
            Log.d(TAG, "wavDataSize : " + wavDataSize);
//            // 4byte配列でないと、ByteBufferでエラーが出る
//            byte[] wavBlockByteData = {0,0,0,0};
//            ByteBuffer intBuffer = ByteBuffer.allocate(Integer.BYTES);
//            for (int i = 0; i < wavDataSize; i+=2){
//
//                // リトルエンディアンで後ろから並べる
//                wavBlockByteData[2] = wavData[i + 1];
//                wavBlockByteData[3] = wavData[i];
//
//                intBuffer.clear();
//                intBuffer.put(wavBlockByteData);
//                intBuffer.flip();
//                long wavBlockIntData = (long) intBuffer.getInt();
//                double gain = 1.0;
//                long result;
//                result = (long) (wavBlockIntData * gain); // gain適用
//
//                // intをbyteに変換
//                intBuffer.clear();
//                intBuffer.putInt((int) result); // intをBufferにセット
//                intBuffer.flip(); // Bufferを読み取りモードにセット
//                intBuffer.get(wavBlockByteData); // Bufferからbyteを取得
//
//                // wavDataを置き換え
//                wavData[i + 1] = wavBlockByteData[2];
//                wavData[i] = wavBlockByteData[3];
//            }
//            for (float i = 0.1f; i <= 1.0F; i+=0.1){
//                Log.d(TAG, "i = " + i);
//                audioTrack.setVolume(i);
//                audioTrack.write(wavData, 0, wavDataSize);
//            }
            audioTrack.setVolume(1.0F);
            audioTrack.write(wavData, 0, wavDataSize);
            input.close();
            audioTrack.stop();
            audioTrack.release();
        } catch (Exception e){
            Log.e(TAG, e.getMessage());
            throw new Exception(e.getMessage());
        }
    }

    private void checkWaveHeaderData(byte[] wavHeaderData){
        // https://qiita.com/goodclues/items/67a20140fd7c205872d6
        // https://www.youfit.co.jp/archives/1418

        // TODO:解析した値でAudioTrackの値をセットするようにすること

        // fmt と data は、固定値で含まれているので基準のIndexとなる
        int fmtIndex = 0;
        // 検索対象のヘッダデータは4バイトで意味を表すもののため
        for (int i = 0; i < wavHeaderData.length - 4; i++){
            // ASCⅡ
            // 'fmt ' => f:0x66, m:0x6d, t:0x74, ' ':0x20
            if (wavHeaderData[i] == 0x66 && wavHeaderData[i+1] == 0x6d
            && wavHeaderData[i+2] == 0x74 && wavHeaderData[i+3] == 0x20){
                fmtIndex = i;
                Log.d(TAG, "fmt index : " + fmtIndex);
                break;
            }
        }
        if (fmtIndex == 0){
            Log.e(TAG, "Not found fmt index");
        }

        int dataIndex = 0;
        for (int i = 0; i < wavHeaderData.length - 4; i++){
            // ASCⅡ
            // 'data' => d:0x64, a:0x61, t:0x74, a:0x61
            if (wavHeaderData[i] == 0x64 && wavHeaderData[i+1] == 0x61
            && wavHeaderData[i+2] == 0x74 && wavHeaderData[i+3] == 0x61){
                dataIndex = i;
                Log.d(TAG, "data index : "+ dataIndex);
                break;
            }
        }
        if (dataIndex == 0){
            Log.e(TAG, "Not found data index");
        }

        if (fmtIndex != 0){
            // チャンネル
            // 上位バイト モノラル:0x01, ステレオ:0x02
            // 下位バイト 0x00
            byte wavChannelUpperByte = wavHeaderData[fmtIndex+10];
            byte wavChannelLowByte = wavHeaderData[fmtIndex+11];
            Log.d(TAG, "チャンネル(上位バイト):0x" + Integer.toHexString(wavChannelUpperByte));
            Log.d(TAG, "チャンネル(下位バイト):0x" + Integer.toHexString(wavChannelLowByte));
            // 4byteで収めないとエラーが出る
            // 後ろから格納 リトルエンディアンてことかな
            byte[] wavChannelByte = {0, 0, wavChannelLowByte, wavChannelUpperByte};
            int wavChannel = ByteBuffer.wrap(wavChannelByte).getInt();
            Log.d(TAG, "チャンネル:" + Integer.toHexString(wavChannel));
            switch (wavChannelUpperByte){
                case 1:
                    Log.d(TAG, "チャンネル:モノラル");
                    mChannelConfig = AudioFormat.CHANNEL_OUT_MONO;
                    break;
                case 2:
                    Log.d(TAG, "チャンネル:ステレオ");
                    mChannelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                    break;
            }

            // サンプリングレート
            // 44100Hz => 0x44AC0000
            byte wavSamplingRateUpperByte1 = wavHeaderData[fmtIndex+12];
            byte wavSamplingRateLowByte1 = wavHeaderData[fmtIndex+13];
            byte wavSamplingRateUpperByte2 = wavHeaderData[fmtIndex+14];
            byte wavSamplingRateLowByte2 = wavHeaderData[fmtIndex+15];
            Log.d(TAG, "サンプリングレート(1byte):0x"+Integer.toHexString(wavSamplingRateUpperByte1));
            Log.d(TAG, "サンプリングレート(2byte):0x"+Integer.toHexString(wavSamplingRateLowByte1));
            Log.d(TAG, "サンプリングレート(3byte):0x"+Integer.toHexString(wavSamplingRateUpperByte2));
            Log.d(TAG, "サンプリングレート(4byte):0x"+Integer.toHexString(wavSamplingRateLowByte2));
            // リトルエンディアン
            byte[] wavSamplingRateByte = {wavSamplingRateLowByte2, wavSamplingRateUpperByte2,
                    wavSamplingRateLowByte1, wavSamplingRateUpperByte1};
            int wavSamplingRate = ByteBuffer.wrap(wavSamplingRateByte).getInt();
            Log.d(TAG, "サンプリングレート:"+wavSamplingRate+"Hz");
            mSampleRate = wavSamplingRate;

            // ブロックサイズ
            // (チャンネル数 * ビット数) / 8bit
            // モノラル 16bit => (1 * 16) / 8 = 2byte
            // ステレオ 16bit => (2 * 16) / 8 = 4byte
            byte wavBlockSizeByte = wavHeaderData[fmtIndex+20];
            Log.d(TAG, "ブロックサイズ:0x"+Integer.toHexString(wavBlockSizeByte));
            switch ((int)wavBlockSizeByte){
                case 0x02:
                    Log.d(TAG, "ブロックサイズ:2byte");
                    break;
                case 0x04:
                    Log.d(TAG, "ブロックサイズ:4byte");
                    break;
            }

            // ビット数
            // 8bit(1byte) => 0x0800
            // 16bit(2byte) => 0x1000
            byte wavBitSize = wavHeaderData[fmtIndex+22];
            Log.d(TAG, "ビット数:0x"+Integer.toHexString(wavBitSize));
            int wavBitByteSize = wavBitSize / 8; // バイト換算
            Log.d(TAG, "ビット数:"+wavBitByteSize+"byte");
            switch ((int)wavBitSize){
                case 0x08:
                    Log.d(TAG, "ビット数:8bit");
                    mAudioFormat = AudioFormat.ENCODING_PCM_8BIT;
                    break;
                case 0x10:
                    Log.d(TAG, "ビット数:16bit");
                    mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
                    break;
            }
        }
        if (dataIndex != 0){
            // wavデータサイズ
            byte wavDataSizeUpperByte1 = wavHeaderData[dataIndex+4];
            byte wavDataSizeLowByte1 = wavHeaderData[dataIndex+5];
            byte wavDataSizeUpperByte2 = wavHeaderData[dataIndex+6];
            byte wavDataSizeLowByte2 = wavHeaderData[dataIndex+7];
            Log.d(TAG, "データサイズ(1byte):0x"+Integer.toHexString(wavDataSizeUpperByte1));
            Log.d(TAG, "データサイズ(2byte):0x"+Integer.toHexString(wavDataSizeLowByte1));
            Log.d(TAG, "データサイズ(3byte):0x"+Integer.toHexString(wavDataSizeUpperByte2));
            Log.d(TAG, "データサイズ(4byte):0x"+Integer.toHexString(wavDataSizeLowByte2));

            byte[] wavDataSizeByte = {wavDataSizeLowByte2, wavDataSizeUpperByte2,
                                    wavDataSizeLowByte1, wavDataSizeUpperByte1};
            int wavDataSize = ByteBuffer.wrap(wavDataSizeByte).getInt();
            Log.d(TAG, "データサイズ(総バイト数) : "+wavDataSize);
            mSoundDataSize = wavDataSize;
            // ヘッダサイズ(データ開始地点までとする)
            mHeaderSize = mAllDataSize - mSoundDataSize;
        }
    }
}