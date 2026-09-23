package dev.andrefrelicot.papershaders.shaders

import dev.andrefrelicot.papershaders.CommonAgsl

internal val MetaballsAgsl: String = CommonAgsl + """

uniform shader u_noiseTexture;
uniform vec2 u_noiseTextureSize;
uniform vec4 u_colorBack;
uniform vec4 u_colors[8];
uniform float u_colorsCount;
uniform float u_size;
uniform float u_count;

vec4 mb_colorAt(int index) {
  if (index <= 0) return u_colors[0];
  if (index == 1) return u_colors[1];
  if (index == 2) return u_colors[2];
  if (index == 3) return u_colors[3];
  if (index == 4) return u_colors[4];
  if (index == 5) return u_colors[5];
  if (index == 6) return u_colors[6];
  return u_colors[7];
}

float mb_randomR(vec2 p) {
  vec2 uv = floor(p) / 100.0 + 0.5;
  return u_noiseTexture.eval(fract(uv) * u_noiseTextureSize).r;
}

float mb_noise(float x) {
  float i = floor(x);
  float f = fract(x);
  float u = f * f * (3.0 - 2.0 * f);
  return mix(mb_randomR(vec2(i, 0.0)), mb_randomR(vec2(i + 1.0, 0.0)), u);
}

float mb_ballShape(vec2 uv, vec2 c, float p) {
  float s = 0.5 * length(uv - c);
  s = 1.0 - clamp(s, 0.0, 1.0);
  return pow(s, p);
}

float mb_totalBallShapeAt(vec2 shapeUv, float t) {
  shapeUv += 0.5;
  float totalShape = 0.0;
  for (int i = 0; i < 20; i++) {
    if (i >= int(ceil(u_count))) break;
    float idxFract = float(i) / 20.0;
    float angle = TWO_PI * idxFract;
    float speed = 1.0 - 0.2 * idxFract;
    float noiseX = mb_noise(angle * 10.0 + float(i) + t * speed);
    float noiseY = mb_noise(angle * 20.0 + float(i) - t * speed);
    vec2 pos = vec2(0.5) + 1e-4 + 0.9 * (vec2(noiseX, noiseY) - 0.5);
    float sizeFrac = 1.0;
    if (float(i) > floor(u_count - 1.0)) sizeFrac *= fract(u_count);
    float shape = mb_ballShape(shapeUv, pos, 45.0 - 30.0 * u_size * sizeFrac);
    shape *= pow(u_size, 0.2);
    totalShape += smoothstep(0.0, 1.0, shape);
  }
  return totalShape;
}

vec4 main(vec2 fragCoord) {
  vec2 shapeUv = ps_objectUV(fragCoord);
  shapeUv += 0.5;
  float t = 0.2 * (u_time + 2503.4);

  vec3 totalColor = vec3(0.0);
  float totalShape = 0.0;
  float totalOpacity = 0.0;

  for (int i = 0; i < 20; i++) {
    if (i >= int(ceil(u_count))) break;
    float idxFract = float(i) / 20.0;
    float angle = TWO_PI * idxFract;
    float speed = 1.0 - 0.2 * idxFract;
    float noiseX = mb_noise(angle * 10.0 + float(i) + t * speed);
    float noiseY = mb_noise(angle * 20.0 + float(i) - t * speed);
    vec2 pos = vec2(0.5) + 1e-4 + 0.9 * (vec2(noiseX, noiseY) - 0.5);

    int safeIndex = int(glsl_mod(float(i), max(u_colorsCount, 1.0)));
    vec4 ballColor = mb_colorAt(safeIndex);
    ballColor.rgb *= ballColor.a;

    float sizeFrac = 1.0;
    if (float(i) > floor(u_count - 1.0)) sizeFrac *= fract(u_count);
    float shape = mb_ballShape(shapeUv, pos, 45.0 - 30.0 * u_size * sizeFrac);
    shape *= pow(u_size, 0.2);
    shape = smoothstep(0.0, 1.0, shape);

    totalColor += ballColor.rgb * shape;
    totalShape += shape;
    totalOpacity += ballColor.a * shape;
  }

  totalColor /= max(totalShape, 1e-4);
  totalOpacity /= max(totalShape, 1e-4);

  vec2 objectUv = ps_objectUV(fragCoord);

  float totalShapeX =
    mb_totalBallShapeAt(
      objectUv + ps_objectPixelStepX(fragCoord),
      t
    );

  float totalShapeY =
    mb_totalBallShapeAt(
      objectUv + ps_objectPixelStepY(fragCoord),
      t
    );

  float edgeWidth =
    max(
      ps_finiteFwidth(
        totalShape,
        totalShapeX,
        totalShapeY
      ),
      ps_pixelDerivative(0.5)
    );

  float finalShape =
    smoothstep(
      0.4,
      0.4 + edgeWidth,
      totalShape
    );

  /*
   * Pseudo-3D gel lighting.
   * The metaball field acts like a height surface.
   */
  float gradientX =
    totalShapeX - totalShape;

  float gradientY =
    totalShapeY - totalShape;

  vec3 normal =
    normalize(
      vec3(
        -gradientX * 720.0,
        -gradientY * 720.0,
        1.0
      )
    );

  /*
   * Slowly moving virtual light.
   */
  float lightSwing =
    0.14 * sin(u_time * 0.30);

  vec3 lightDirection =
    normalize(
      vec3(
        -0.58 + lightSwing,
        -0.68,
        0.92
      )
    );

  vec3 viewDirection =
    vec3(
      0.0,
      0.0,
      1.0
    );

  /*
   * Diffuse curvature.
   */
  float diffuse =
    max(
      dot(
        normal,
        lightDirection
      ),
      0.0
    );

  /*
   * Glossy highlight.
   */
  vec3 halfVector =
    normalize(
      lightDirection +
      viewDirection
    );

  float specular =
    pow(
      max(
        dot(
          normal,
          halfVector
        ),
        0.0
      ),
      30.0
    );

  /*
   * Fresnel-like edge illumination.
   */
  float rim =
    pow(
      1.0 -
      clamp(
        normal.z,
        0.0,
        1.0
      ),
      2.25
    );

  /*
   * Approximate optical thickness.
   */
  float thickness =
    clamp(
      totalShape * 0.38,
      0.0,
      1.0
    );

  /*
   * Base volumetric colour.
   */
  vec3 gelColor =
    totalColor *
    (
      0.52 +
      diffuse * 0.78
    );

  /*
   * Soft inner illumination.
   */
  gelColor +=
    mix(
      totalColor,
      vec3(0.82, 0.92, 1.0),
      0.24
    ) *
    thickness *
    0.30;

  /*
   * Bright specular reflection.
   */
  gelColor +=
    vec3(1.0) *
    specular *
    0.95;

  /*
   * Translucent illuminated rim.
   */
  gelColor +=
    mix(
      totalColor,
      vec3(1.0),
      0.52
    ) *
    rim *
    0.50;

  /*
   * Mild shading on the opposite side.
   */
  float shadow =
    mix(
      0.72,
      1.0,
      diffuse
    );

  gelColor *= shadow;

  vec3 color =
    gelColor *
    finalShape;

  float opacity =
    totalOpacity *
    finalShape;
  vec3 bgColor = u_colorBack.rgb * u_colorBack.a;
  color = color + bgColor * (1.0 - opacity);
  opacity = opacity + u_colorBack.a * (1.0 - opacity);

  color += 0.00390625 * (
    fract(sin(dot(0.014 * vec2(fragCoord.x, u_resolution.y - fragCoord.y), vec2(12.9898, 78.233))) * 43758.5453123) - 0.5
  );

  return vec4(color, opacity);
}
"""

