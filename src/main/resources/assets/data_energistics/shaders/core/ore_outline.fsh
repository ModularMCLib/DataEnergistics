#version 150

in vec4 vertexColor;
in vec2 horizontalPosition;
uniform vec4 ColorModulator;
uniform vec2 ScanCenter;
uniform float ScanRadiusSquared;
out vec4 fragColor;

void main() {
    vec2 offset = horizontalPosition - ScanCenter;
    if (dot(offset, offset) > ScanRadiusSquared || vertexColor.a == 0.0) discard;
    fragColor = vertexColor * ColorModulator;
}
