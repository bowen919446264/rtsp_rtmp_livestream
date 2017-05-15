precision mediump float;

varying highp vec2 vTextureCoord;

uniform sampler2D uTexture;
uniform sampler2D uTexture2;
uniform sampler2D uTexture3;
uniform float uSmoothDegree;

void main() {
    highp vec4 bilateral = texture2D(uTexture, vTextureCoord);
    highp vec4 canny = texture2D(uTexture2, vTextureCoord);
    highp vec4 origin = texture2D(uTexture3, vTextureCoord);

    highp vec4 smooth_;
    lowp float r = origin.r;
    lowp float g = origin.g;
    lowp float b = origin.b;
    if (canny.r < 0.2
        && r > 0.3725 && g > 0.1568 && b > 0.0784
        && r > b && (max(max(r, g), b) - min(min(r, g), b)) > 0.0588 &&
        abs(r-g) > 0.0588) {
        smooth_ = (1.0 - uSmoothDegree) * (origin - bilateral) + bilateral;
    }
    else {
        smooth_ = origin;
    }
    smooth_.r = log(1.0 + 0.2 * smooth_.r)/log(1.2);
    smooth_.g = log(1.0 + 0.2 * smooth_.g)/log(1.2);
    smooth_.b = log(1.0 + 0.2 * smooth_.b)/log(1.2);
    gl_FragColor = smooth_;
}