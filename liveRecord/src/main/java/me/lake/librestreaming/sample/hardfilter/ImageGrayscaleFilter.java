package me.lake.librestreaming.sample.hardfilter;

import me.lake.librestreaming.filter.hardvideofilter.BaseHardVideoFilter;
import me.lake.librestreaming.filter.hardvideofilter.OriginalHardVideoFilter;

/**
 * Created by liuwb on 2017/3/30.
 */
public class ImageGrayscaleFilter extends BaseImageFilter {

    public static final String GRAYSCALE_FRAGMENT_SHADER = "" +
            "precision highp float;\n" +
            "\n" +
            "varying vec2 textureCoordinate;\n" +
            "\n" +
            "uniform sampler2D inputImageTexture;\n" +
            "\n" +
            "const highp vec3 W = vec3(0.2125, 0.7154, 0.0721);\n" +
            "\n" +
            "void main()\n" +
            "{\n" +
            "  lowp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "  float luminance = dot(textureColor.rgb, W);\n" +
            "\n" +
            "  gl_FragColor = vec4(vec3(luminance), textureColor.a);\n" +
            "}";

    public ImageGrayscaleFilter() {
        super(NO_FILTER_VERTEX_SHADER, GRAYSCALE_FRAGMENT_SHADER);
    }
}
