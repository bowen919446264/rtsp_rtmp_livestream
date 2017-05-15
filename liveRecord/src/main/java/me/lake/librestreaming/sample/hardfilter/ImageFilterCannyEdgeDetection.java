package me.lake.librestreaming.sample.hardfilter;

import android.content.Context;
import jp.co.cyberagent.android.gpuimage.GPUImageDirectionalSobelEdgeDetectionFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageGaussianBlurFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageGrayscaleFilter;
import jp.co.cyberagent.android.gpuimage.GPUImageNonMaximumSuppressionFilter;
import me.lake.librestreaming.filter.hardvideofilter.BaseHardVideoFilter;
import me.lake.librestreaming.filter.hardvideofilter.HardVideoGroupFilter;
import me.lake.librestreaming.sample.hardfilter.extra.GPUImageCompatibleFilter;
import me.lake.librestreaming.sample.hardfilter.extra.GPUImageGaussianBlurSingleFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by liuwb on 2017/3/30.
 */
public class ImageFilterCannyEdgeDetection extends HardVideoGroupFilter {

    public ImageFilterCannyEdgeDetection(List<BaseHardVideoFilter> filters) {
        super(filters);
    }

    public static ImageFilterCannyEdgeDetection newInstance(float blurSize) {
        ArrayList<BaseHardVideoFilter> filters = new ArrayList<>();
        BaseHardVideoFilter baseHFilter = new ImageGrayscaleFilter();
        filters.add(baseHFilter);
        baseHFilter = new GPUImageCompatibleFilter(new GPUImageGaussianBlurSingleFilter(blurSize, true));
        filters.add(baseHFilter);
        baseHFilter = new GPUImageCompatibleFilter(new GPUImageGaussianBlurSingleFilter(blurSize, false));
        filters.add(baseHFilter);
        baseHFilter = new GPUImageCompatibleFilter(new GPUImageDirectionalSobelEdgeDetectionFilter());
        filters.add(baseHFilter);
        baseHFilter = new GPUImageCompatibleFilter(new GPUImageNonMaximumSuppressionFilter());
        filters.add(baseHFilter);

        ImageFilterCannyEdgeDetection cannyEdgeDetection = new ImageFilterCannyEdgeDetection(filters);
        return cannyEdgeDetection;
    }
}
