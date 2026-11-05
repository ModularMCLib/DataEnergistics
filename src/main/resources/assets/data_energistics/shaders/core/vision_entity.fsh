#version 150

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
    fragColor = vec4(vertexColor.rgb, 1.0);
}
