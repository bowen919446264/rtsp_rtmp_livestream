package me.lake.librestreaming.sample.hardfilter.extra;

import android.content.Context;
import android.opengl.GLES20;
import jp.co.cyberagent.android.gpuimage.GPUImageFilter;
import me.lake.librestreaming.sample.R;
import me.lake.librestreaming.sample.utils.RecordUtils;

/**
 * 采用重MagicCamera的Studio方案相同的GLSL方案
 * Created by liuwb on 2017/3/9.
 */
public class GPUImageBeautyFilter extends GPUImageFilter {
    private int mSingleStepOffsetLocation;
    private int mParamsLocation;

    public GPUImageBeautyFilter(Context context) {
        super(NO_FILTER_VERTEX_SHADER,
                RecordUtils.readShaderFromRawResource(context, R.raw.beauty));
    }

    public void onInit() {
        super.onInit();
        mSingleStepOffsetLocation = GLES20.glGetUniformLocation(getProgram(), "singleStepOffset");
        mParamsLocation = GLES20.glGetUniformLocation(getProgram(), "params");
        setBeautyLevel(1);
    }

    private void setTexelSize(final float w, final float h) {
        setFloatVec2(mSingleStepOffsetLocation, new float[]{2.0f / w, 2.0f / h});
    }

    @Override
    public void onOutputSizeChanged(final int width, final int height) {
        super.onOutputSizeChanged(width, height);
        setTexelSize(width, height);
    }

    public void setBeautyFloat(float beautyEffect) {
        setFloat(mParamsLocation, beautyEffect);
    }

    public void setBeautyLevel(int level) {
        switch (level) {
            case 1:
                setFloat(mParamsLocation, 1.0f);
                break;
            case 2:
                setFloat(mParamsLocation, 0.8f);
                break;
            case 3:
                setFloat(mParamsLocation, 0.6f);
                break;
            case 4:
                setFloat(mParamsLocation, 0.4f);
                break;
            case 5:
                setFloat(mParamsLocation, 0.33f);
                break;
            default:
                break;
        }
    }

}
