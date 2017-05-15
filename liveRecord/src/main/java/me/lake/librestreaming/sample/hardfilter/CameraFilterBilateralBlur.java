package me.lake.librestreaming.sample.hardfilter;

import jp.co.cyberagent.android.gpuimage.GPUImageBilateralFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageFilter;
import me.lake.librestreaming.filter.hardvideofilter.BaseHardVideoFilter;
import me.lake.librestreaming.filter.hardvideofilter.HardVideoGroupFilter;
import me.lake.librestreaming.sample.hardfilter.extra.GPUImageCompatibleFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by liuwb on 2017/3/30.
 */
public class CameraFilterBilateralBlur extends HardVideoGroupFilter {

    public CameraFilterBilateralBlur(List<BaseHardVideoFilter> filters) {
        super(filters);
    }

    public static CameraFilterBilateralBlur newInstance() {
        ArrayList<BaseHardVideoFilter> filters = new ArrayList<>();
        filters.add(new GPUImageCompatibleFilter<>(new GPUImageBilateralFilter(6f)));
        filters.add(new GPUImageCompatibleFilter<>(new GPUImageFilter()));

        CameraFilterBilateralBlur bilateralBlur = new CameraFilterBilateralBlur(filters);
        return bilateralBlur;
    }
}
