package me.lake.librestreaming.sample.hardfilter.extra;

import android.content.Context;
import android.opengl.GLES20;
import jp.co.cyberagent.android.gpuimage.GPUImageFilter;
import me.lake.librestreaming.sample.R;
import me.lake.librestreaming.sample.utils.RecordUtils;

/**
 * 采用和IOS相同的GLSL滤镜代码
 */
public class MagicBeautyFilter extends GPUImageFilter {
    private int mSingleStepOffsetLocation;
    private int mParamsLocation;

    private float mRatio = 1.5f;

    public MagicBeautyFilter(Context context) {
        super(NO_FILTER_VERTEX_SHADER,
                RecordUtils.readShaderFromRawResource(context, R.raw.beautify_fragment));
    }

    public void onInit() {
        super.onInit();
        mSingleStepOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "singleStepOffset");
        mParamsLocation = GLES20.glGetUniformLocation(getProgram(), "params");
        setBeautyLevel(1);
    }

    public void onDestroy() {
        super.onDestroy();
    }

    private void setTexelSize(final float w, final float h) {
        setFloatVec2(mSingleStepOffsetLocation, new float[]{mRatio / w, mRatio / h});
    }

    @Override
    public void onOutputSizeChanged(final int width, final int height) {
        super.onOutputSizeChanged(width, height);
        setTexelSize(width, height);
    }

    public void setBeautyLevel(int level) {
        switch (level) {
            case 1:
                setFloatVec4(mParamsLocation, new float[]{1.0f, 1.0f, 0.15f, 0.15f});
                break;
            case 2:
                setFloatVec4(mParamsLocation, new float[]{0.8f, 0.9f, 0.2f, 0.2f});
                break;
            case 3:
                setFloatVec4(mParamsLocation, new float[]{0.6f, 0.8f, 0.25f, 0.25f});
                break;
            case 4:
                setFloatVec4(mParamsLocation, new float[]{0.4f, 0.7f, 0.38f, 0.3f});
                break;
            case 5:
                setFloatVec4(mParamsLocation, new float[]{0.33f, 0.63f, 0.4f, 0.35f});
                break;
            default:
                break;
        }
    }

    public void setBeautyFloat(float floatR) {
        float[] floats = null;
        if (floatR <= 0.33f) {
            floats = new float[]{floatR, 0.63f, 0.4f, 0.35f};
        } else if (floatR <= 0.4f) {
            floats = new float[]{floatR, 0.7f, 0.38f, 0.3f};
        } else if (floatR <= 0.6f) {
            floats = new float[]{floatR, 0.8f, 0.25f, 0.25f};
        } else if (floatR <= 0.8f) {
            floats = new float[]{floatR, 0.9f, 0.2f, 0.2f};
        } else {
            if (floatR > 1) {
                floatR = 1.0f;
            }
            floats = new float[]{floatR, 1.0f, 0.15f, 0.15f};
        }
        setFloatVec4(mParamsLocation, floats);
    }


}
