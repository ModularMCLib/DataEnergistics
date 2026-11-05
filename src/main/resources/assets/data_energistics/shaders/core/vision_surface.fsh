#version 150

in vec4 vertexColor;
in vec3 relativePosition;
uniform float ScanRadiusSquared;
out vec4 fragColor;

void main() {
    if (ScanRadiusSquared > 0.0 && dot(relativePosition, relativePosition) > ScanRadiusSquared) discard;
    fragColor = vertexColor;
}
