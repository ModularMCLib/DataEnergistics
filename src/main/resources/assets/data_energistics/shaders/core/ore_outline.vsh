#version 150

in vec3 Position;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 SectionOrigin;
out vec4 vertexColor;
out vec2 horizontalPosition;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    horizontalPosition = SectionOrigin + Position.xz;
}
