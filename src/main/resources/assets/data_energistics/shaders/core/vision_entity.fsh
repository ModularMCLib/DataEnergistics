#version 150

#moj_import <data_energistics:vision_sonar.glsl>

uniform sampler2D Sampler0;
uniform float ScanRadiusSquared;
uniform float Textured;
in vec2 texCoord;
in vec4 vertexColor;
in vec3 relativePosition;
out vec4 fragColor;

void main() {
    if (Textured > 0.5 && texture(Sampler0, texCoord).a < 0.1) discard;
    if (ScanRadiusSquared > 0.0 && dot(relativePosition, relativePosition) > ScanRadiusSquared) discard;
    float echo = ScanRadiusSquared > 0.0 ? sonarEcho(relativePosition) : 1.0;
    if (echo < 0.005) discard;
    fragColor = vec4(vertexColor.rgb * echo, 1.0);
}
