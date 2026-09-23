package dev.andrefrelicot.papershaders

internal const val CommonAgsl = """
uniform vec2 u_resolution;
uniform float u_pixelRatio;
uniform float u_time;
uniform float u_fit;
uniform float u_scale;
uniform float u_rotation;
uniform float u_originX;
uniform float u_originY;
uniform float u_offsetX;
uniform float u_offsetY;
uniform float u_worldWidth;
uniform float u_worldHeight;
uniform float u_imageAspectRatio;

const float PI = 3.14159265358979323846;
const float TWO_PI = 6.28318530718;

float glsl_mod(float x, float y) {
  return x - y * floor(x / y);
}

vec3 permute(vec3 x) {
  return mod(((x * 34.0) + 1.0) * x, 289.0);
}

float snoise(vec2 v) {
  const vec4 C = vec4(0.211324865405187, 0.366025403784439,
    -0.577350269189626, 0.024390243902439);
  vec2 i = floor(v + dot(v, C.yy));
  vec2 x0 = v - i + dot(i, C.xx);
  vec2 i1;
  i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
  vec4 x12 = x0.xyxy + C.xxzz;
  x12.xy -= i1;
  i = mod(i, 289.0);
  vec3 p = permute(permute(i.y + vec3(0.0, i1.y, 1.0))
    + i.x + vec3(0.0, i1.x, 1.0));
  vec3 m = max(0.5 - vec3(dot(x0, x0), dot(x12.xy, x12.xy),
      dot(x12.zw, x12.zw)), 0.0);
  m = m * m;
  m = m * m;
  vec3 x = 2.0 * fract(p * C.www) - 1.0;
  vec3 h = abs(x) - 0.5;
  vec3 ox = floor(x + 0.5);
  vec3 a0 = x - ox;
  m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
  vec3 g;
  g.x = a0.x * x0.x + h.x * x0.y;
  g.yz = a0.yz * x12.xz + h.yz * x12.yw;
  return 130.0 * dot(m, g);
}

vec3 ps_getBoxSize(float boxRatio, vec2 givenBoxSize, float fit, vec2 resolution) {
  vec2 box = vec2(0.0);
  box.x = boxRatio * min(givenBoxSize.x / boxRatio, givenBoxSize.y);
  float noFitBoxWidth = box.x;
  if (fit == 1.0) {
    box.x = boxRatio * min(resolution.x / boxRatio, resolution.y);
  } else if (fit == 2.0) {
    box.x = boxRatio * max(resolution.x / boxRatio, resolution.y);
  }
  box.y = box.x / boxRatio;
  return vec3(box, noFitBoxWidth);
}

vec2 ps_baseUVAt(vec2 pos) {
  return vec2(pos.x / u_resolution.x - 0.5, 0.5 - pos.y / u_resolution.y);
}

float ps_pixelDerivative(float multiplier) {
  return max(multiplier / max(min(u_resolution.x, u_resolution.y), 1.0), 1e-4);
}

float ps_finiteFwidth(float center, float alongX, float alongY) {
  return abs(alongX - center) + abs(alongY - center);
}

vec2 ps_objectUVAt(vec2 pos) {
  vec2 uv = ps_baseUVAt(pos);
  vec2 boxOrigin = vec2(0.5 - u_originX, u_originY - 0.5);
  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.0)) * u_pixelRatio;
  float r = u_rotation * PI / 180.0;
  mat2 graphicRotation = mat2(cos(r), sin(r), -sin(r), cos(r));
  vec2 graphicOffset = vec2(-u_offsetX, u_offsetY);

  vec2 fixedRatioBoxGivenSize = vec2(
    (u_worldWidth == 0.0) ? u_resolution.x : givenBoxSize.x,
    (u_worldHeight == 0.0) ? u_resolution.y : givenBoxSize.y
  );
  vec2 objectBoxSize = ps_getBoxSize(1.0, fixedRatioBoxGivenSize, u_fit, u_resolution).xy;
  vec2 objectWorldScale = u_resolution / objectBoxSize;

  vec2 objectUV = uv;
  objectUV *= objectWorldScale;
  objectUV += boxOrigin * (objectWorldScale - 1.0);
  objectUV += graphicOffset;
  objectUV /= u_scale;
  objectUV = graphicRotation * objectUV;
  return objectUV;
}

vec2 ps_objectUV(vec2 fragCoord) {
  return ps_objectUVAt(fragCoord);
}

vec2 ps_objectBoxSize() {
  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.0)) * u_pixelRatio;
  vec2 fixedRatioBoxGivenSize = vec2(
    (u_worldWidth == 0.0) ? u_resolution.x : givenBoxSize.x,
    (u_worldHeight == 0.0) ? u_resolution.y : givenBoxSize.y
  );
  return ps_getBoxSize(1.0, fixedRatioBoxGivenSize, u_fit, u_resolution).xy;
}

vec2 ps_objectPixelStepX(vec2 fragCoord) {
  return ps_objectUVAt(fragCoord + vec2(1.0, 0.0)) - ps_objectUVAt(fragCoord);
}

vec2 ps_objectPixelStepY(vec2 fragCoord) {
  return ps_objectUVAt(fragCoord + vec2(0.0, 1.0)) - ps_objectUVAt(fragCoord);
}

vec2 ps_patternUVAt(vec2 pos) {
  vec2 uv = ps_baseUVAt(pos);
  vec2 boxOrigin = vec2(0.5 - u_originX, u_originY - 0.5);
  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.0)) * u_pixelRatio;
  float r = u_rotation * PI / 180.0;
  mat2 graphicRotation = mat2(cos(r), sin(r), -sin(r), cos(r));
  vec2 graphicOffset = vec2(-u_offsetX, u_offsetY);

  vec2 patternBoxGivenSize = vec2(
    (u_worldWidth == 0.0) ? u_resolution.x : givenBoxSize.x,
    (u_worldHeight == 0.0) ? u_resolution.y : givenBoxSize.y
  );
  float patternBoxRatio = patternBoxGivenSize.x / patternBoxGivenSize.y;
  vec3 boxSizeData = ps_getBoxSize(patternBoxRatio, patternBoxGivenSize, u_fit, u_resolution);
  vec2 patternBoxScale = u_resolution / boxSizeData.xy;

  vec2 patternUV = uv;
  patternUV += graphicOffset / patternBoxScale;
  patternUV += boxOrigin;
  patternUV -= boxOrigin / patternBoxScale;
  patternUV *= u_resolution;
  patternUV /= u_pixelRatio;
  if (u_fit > 0.0) {
    patternUV *= (boxSizeData.z / boxSizeData.x);
  }
  patternUV /= u_scale;
  patternUV = graphicRotation * patternUV;
  patternUV += boxOrigin / patternBoxScale;
  patternUV -= boxOrigin;
  patternUV *= 0.01;
  return patternUV;
}

vec2 ps_patternUV(vec2 fragCoord) {
  return ps_patternUVAt(fragCoord);
}

vec2 ps_patternBoxSize() {
  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.0)) * u_pixelRatio;
  vec2 patternBoxGivenSize = vec2(
    (u_worldWidth == 0.0) ? u_resolution.x : givenBoxSize.x,
    (u_worldHeight == 0.0) ? u_resolution.y : givenBoxSize.y
  );
  float patternBoxRatio = patternBoxGivenSize.x / patternBoxGivenSize.y;
  return ps_getBoxSize(patternBoxRatio, patternBoxGivenSize, u_fit, u_resolution).xy;
}

vec2 ps_responsiveBoxGivenSize() {
  vec2 givenBoxSize = max(vec2(u_worldWidth, u_worldHeight), vec2(1.0)) * u_pixelRatio;
  return vec2(
    (u_worldWidth == 0.0) ? u_resolution.x : givenBoxSize.x,
    (u_worldHeight == 0.0) ? u_resolution.y : givenBoxSize.y
  );
}

vec2 ps_responsiveUVAt(vec2 pos) {
  vec2 uv = ps_baseUVAt(pos);
  vec2 boxOrigin = vec2(0.5 - u_originX, u_originY - 0.5);
  float r = u_rotation * PI / 180.0;
  mat2 graphicRotation = mat2(cos(r), sin(r), -sin(r), cos(r));
  vec2 graphicOffset = vec2(-u_offsetX, u_offsetY);

  vec2 responsiveBoxGivenSize = ps_responsiveBoxGivenSize();
  float responsiveRatio = responsiveBoxGivenSize.x / responsiveBoxGivenSize.y;
  vec2 responsiveBoxSize = ps_getBoxSize(responsiveRatio, responsiveBoxGivenSize, u_fit, u_resolution).xy;
  vec2 responsiveBoxScale = u_resolution / responsiveBoxSize;

  vec2 responsiveUV = uv;
  responsiveUV *= responsiveBoxScale;
  responsiveUV += boxOrigin * (responsiveBoxScale - 1.0);
  responsiveUV += graphicOffset;
  responsiveUV /= u_scale;
  responsiveUV.x *= responsiveRatio;
  responsiveUV = graphicRotation * responsiveUV;
  responsiveUV.x /= responsiveRatio;
  return responsiveUV;
}

vec2 ps_responsiveUV(vec2 fragCoord) {
  return ps_responsiveUVAt(fragCoord);
}

vec2 ps_imageUVAt(vec2 pos) {
  vec2 uv = ps_baseUVAt(pos);
  vec2 boxOrigin = vec2(0.5 - u_originX, u_originY - 0.5);
  float r = u_rotation * PI / 180.0;
  mat2 graphicRotation = mat2(cos(r), sin(r), -sin(r), cos(r));
  vec2 graphicOffset = vec2(-u_offsetX, u_offsetY);
  vec2 imageBoxSize = vec2(0.0);
  if (u_fit == 1.0) {
    imageBoxSize.x = min(u_resolution.x / u_imageAspectRatio, u_resolution.y) * u_imageAspectRatio;
  } else if (u_fit == 2.0) {
    imageBoxSize.x = max(u_resolution.x / u_imageAspectRatio, u_resolution.y) * u_imageAspectRatio;
  } else {
    imageBoxSize.x = min(10.0, 10.0 / u_imageAspectRatio * u_imageAspectRatio);
  }
  imageBoxSize.y = imageBoxSize.x / u_imageAspectRatio;
  vec2 imageBoxScale = u_resolution / imageBoxSize;
  vec2 imageUV = uv;
  imageUV *= imageBoxScale;
  imageUV += boxOrigin * (imageBoxScale - 1.0);
  imageUV += graphicOffset;
  imageUV /= u_scale;
  imageUV.x *= u_imageAspectRatio;
  imageUV = graphicRotation * imageUV;
  imageUV.x /= u_imageAspectRatio;
  imageUV += 0.5;
  imageUV.y = 1.0 - imageUV.y;
  return imageUV;
}

vec2 ps_imageUV(vec2 fragCoord) {
  return ps_imageUVAt(fragCoord);
}

vec2 ps_patternPixelStepX(vec2 fragCoord) {
  return ps_patternUVAt(fragCoord + vec2(1.0, 0.0)) - ps_patternUVAt(fragCoord);
}

vec2 ps_patternPixelStepY(vec2 fragCoord) {
  return ps_patternUVAt(fragCoord + vec2(0.0, 1.0)) - ps_patternUVAt(fragCoord);
}
"""
