#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // Skia's raster surface is premultiplied and the composite pass blends with
    // one / 1-srcalpha, so the sample goes straight out.
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
