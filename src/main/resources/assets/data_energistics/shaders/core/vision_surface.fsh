#version 150

#moj_import <data_energistics:vision_sonar.glsl>

in vec4 vertexColor;
in vec3 relativePosition;
uniform float ScanRadiusSquared;
out vec4 fragColor;

void main() {
    if (ScanRadiusSquared > 0.0 && dot(relativePosition, relativePosition) > ScanRadiusSquared) discard;
    fragColor = vertexColor;
    if (ScanRadiusSquared > 0.0) fragColor.a *= sonarEcho(relativePosition);
}
