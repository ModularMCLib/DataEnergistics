#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float FlowTime;
uniform float DomainWidth;
uniform vec2 HeightRange;
uniform sampler2D SurfaceHeights;

out vec4 vertexColor;
out vec2 pathCoordinate;
out vec2 boundaryPosition;
out float wallFraction;

float groundHeight(vec2 position) {
    vec2 size = vec2(textureSize(SurfaceHeights, 0));
    vec2 grid = clamp(position / DomainWidth, 0.0, 1.0) * (size - 1.0);
    vec2 encoded = texture(SurfaceHeights, (grid + 0.5) / size).rg;
    return HeightRange.x + dot(encoded, vec2(255.0, 65280.0)) / 65535.0 * HeightRange.y + 0.035;
}

void main() {
    float phase = Color.r * 6.2831853;
    float clock = FlowTime * (0.35 + Color.r * 0.12) + phase;
    vec2 drift = vec2(sin(clock) * 3.0, cos(clock * 0.73 + phase) * 2.0);
    vec3 position = Position;
    float bottom = groundHeight(Position.xz);
    wallFraction = (Position.y - bottom + drift.y) / max(20.0 - bottom, 0.1);
    position.xz += abs(Normal.x) > 0.5 ? vec2(0.0, drift.x) : vec2(drift.x, 0.0);
    position.y = mix(groundHeight(position.xz), 20.0, wallFraction);
    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    vertexColor = Color;
    pathCoordinate = UV0;
    boundaryPosition = position.xz;
}
