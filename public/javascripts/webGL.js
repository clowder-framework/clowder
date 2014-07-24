/*
 * Copyright (c) 2010 Brandon Jones
 *
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 *    1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 *
 *    2. Altered source versions must be plainly marked as such, and must not
 *    be misrepresented as being the original software.
 *
 *    3. This notice may not be removed or altered from any source
 *    distribution.\
 *    
 *    Edited by: Luis J Mendez 2011 , NCSA
 */

/* 
 * glMatrix.js - High performance matrix and vector operations for WebGL
 * version 0.9.4
 */
 
// Fallback for systems that don't support WebGL
if(typeof Float32Array != 'undefined') {
  glMatrixArrayType = Float32Array;
} else {
  glMatrixArrayType = Array;
}

/*
 * vec3 - 3 Dimensional Vector
 */
var vec3 = {};

/*
 * vec3.create
 * Creates a new instance of a vec3 using the default array type
 * Any javascript array containing at least 3 numeric elements can serve as a vec3
 *
 * Params:
 * vec - Optional, vec3 containing values to initialize with
 *
 * Returns:
 * New vec3
 */
vec3.create = function(vec) {
  var dest = new glMatrixArrayType(3);
  
  if(vec) {
    dest[0] = vec[0];
    dest[1] = vec[1];
    dest[2] = vec[2];
  }
  
  return dest;
};

vec4 = {};
vec4.create = function(vec) {
  var dest = new glMatrixArrayType(4);
  
  if(vec) {
    dest[0] = vec[0];
    dest[1] = vec[1];
    dest[2] = vec[2];
    dest[3] = vec[3];
  }
  
  return dest;
};
vec4.set = function(vec, dest) {
  dest[0] = vec[0];
  dest[1] = vec[1];
  dest[2] = vec[2];
  dest[3] = vec[3];
};
vec4.setLeft = function(dest, vec) {
  dest[0] = vec[0];
  dest[1] = vec[1];
  dest[2] = vec[2];
  dest[3] = vec[3];
};

vec2 = {};
vec2.create = function(vec) {
  var dest = new glMatrixArrayType(2);
  
  if(vec) {
    dest[0] = vec[0];
    dest[1] = vec[1];
  }
  
  return dest;
};
vec2.set = function(vec, dest) {
  dest[0] = vec[0];
  dest[1] = vec[1];
};
vec2.setLeft = function(dest, vec) {
  dest[0] = vec[0];
  dest[1] = vec[1];
};

/*
 * vec3.set
 * Copies the values of one vec3 to another
 *
 * Params:
 * vec - vec3 containing values to copy
 * dest - vec3 receiving copied values
 *
 * Returns:
 * dest
 */
vec3.set = function(vec, dest) {
  dest[0] = vec[0];
  dest[1] = vec[1];
  dest[2] = vec[2];
  
  return dest;
};

vec3.setLeft = function(dest, vec) {
  dest[0] = vec[0];
  dest[1] = vec[1];
  dest[2] = vec[2];
  
  return dest;
};

vec3.set3 = function(value, dest) {
  dest[0] = dest[1] = dest[2] = value;
  return dest;
};

/*
 * vec3.add
 * Performs a vector addition
 *
 * Params:
 * vec - vec3, first operand
 * vec2 - vec3, second operand
 * dest - Optional, vec3 receiving operation result. If not specified result is written to vec
 *
 * Returns:
 * dest if specified, vec otherwise
 */
vec3.add = function(vec, vec2, dest) {
  if(!dest || vec == dest) {
    vec[0] += vec2[0];
    vec[1] += vec2[1];
    vec[2] += vec2[2];
    return vec;
  }
  
  dest[0] = vec[0] + vec2[0];
  dest[1] = vec[1] + vec2[1];
  dest[2] = vec[2] + vec2[2];
  return dest;
};

/*
 * vec3.subtract
 * Performs a vector subtraction
 *
 * Params:
 * vec - vec3, first operand
 * vec2 - vec3, second operand
 * dest - Optional, vec3 receiving operation result. If not specified result is written to vec
 *
 * Returns:
 * dest if specified, vec otherwise
 */
vec3.subtract = function(vec, vec2, dest) {
  if(!dest || vec == dest) {
    vec[0] -= vec2[0];
    vec[1] -= vec2[1];
    vec[2] -= vec2[2];
    return vec;
  }
  
  dest[0] = vec[0] - vec2[0];
  dest[1] = vec[1] - vec2[1];
  dest[2] = vec[2] - vec2[2];
  return dest;
};
vec3.sub = vec3.subtract;

/*
 * vec3.negate
 * Negates the components of a vec3
 *
 * Params:
 * vec - vec3 to negate
 * dest - Optional, vec3 receiving operation result. If not specified result is written to vec
 *
 * Returns:
 * dest if specified, vec otherwise
 */
vec3.negate = function(vec, dest) {
  if(!dest) { dest = vec; }
  
  dest[0] = -vec[0];
  dest[1] = -vec[1];
  dest[2] = -vec[2];
  return dest;
};

/*
 * vec3.scale
 * Multiplies the components of a vec3 by a scalar value
 *
 * Params:
 * vec - vec3 to scale
 * val - Numeric value to scale by
 * dest - Optional, vec3 receiving operation result. If not specified result is written to vec
 *
 * Returns:
 * dest if specified, vec otherwise
 */
vec3.scale = function(vec, val, dest) {
  if(!dest || vec == dest) {
    vec[0] *= val;
    vec[1] *= val;
    vec[2] *= val;
    return vec;
  }
  
  dest[0] = vec[0]*val;
  dest[1] = vec[1]*val;
  dest[2] = vec[2]*val;
  return dest;
};

/*
 * vec3.multiply
 * Multiplies the components of a vec3 by a vec3
 *
 * Params:
 * vec - vec3 to scale
 * vec3 - vec3 to scale by
 * dest - Optional, vec3 receiving operation result. If not specified result is written to vec
 *
 * Returns:
 * dest if specified, vec otherwise
 */
vec3.multiply = function(vec, vec2, dest) {
  if(!dest || vec == dest) {
    vec[0] *= vec2[0];
    vec[1] *= vec2[1];
    vec[2] *= vec2[2];
    return vec;
  }
  
  dest[0] = vec[0]*vec2[0];
  dest[1] = vec[1]*vec2[1];
  dest[2] = vec[2]*vec2[2];
  return dest;
};
vec3.mul = vec3.multiply;

/*
 * vec3.normalize
 * Generates a unit vector of the same direction as the provided vec3
 * If vector length is 0, returns [0, 0, 0]
 *
 * Params:
 * vec - vec3 to normalize
 * dest - Optional, vec3 receiving operation result. If not specified result is written to vec
 *
 * Returns:
 * dest if specified, vec otherwise
 */
vec3.normalize = function(vec, dest) {
  if(!dest) { dest = vec; }
  
  var x = vec[0], y = vec[1], z = vec[2];
  var len = Math.sqrt(x*x + y*y + z*z);
  
  if (!len) {
    dest[0] = 0;
    dest[1] = 0;
    dest[2] = 0;
    return dest;
  } else if (len == 1) {
    dest[0] = x;
    dest[1] = y;
    dest[2] = z;
    return dest;
  }
  
  len = 1 / len;
  dest[0] = x*len;
  dest[1] = y*len;
  dest[2] = z*len;
  return dest;
};

/*
 * vec3.cross
 * Generates the cross product of two vec3s
 *
 * Params:
 * vec - vec3, first operand
 * vec2 - vec3, second operand
 * dest - Optional, vec3 receiving operation result. If not specified result is written to vec
 *
 * Returns:
 * dest if specified, vec otherwise
 */
vec3.cross = function(vec, vec2, dest){
  if(!dest) { dest = vec; }
  
  var x = vec[0], y = vec[1], z = vec[2];
  var x2 = vec2[0], y2 = vec2[1], z2 = vec2[2];
  
  dest[0] = y*z2 - z*y2;
  dest[1] = z*x2 - x*z2;
  dest[2] = x*y2 - y*x2;
  return dest;
};

/*
 * vec3.length
 * Caclulates the length of a vec3
 *
 * Params:
 * vec - vec3 to calculate length of
 *
 * Returns:
 * Length of vec
 */
vec3.length = function(vec){
  var x = vec[0], y = vec[1], z = vec[2];
  return Math.sqrt(x*x + y*y + z*z);
};

/*
 * vec3.lengthSquare
 * Caclulates the square of the length of a vec3
 *
 * Params:
 * vec - vec3 to calculate length of
 *
 * Returns:
 * Square of the length of vec
 */
vec3.lengthSquare = function(vec){
  var x = vec[0], y = vec[1], z = vec[2];
  return x*x + y*y + z*z;
};

/*
 * vec3.dot
 * Caclulates the dot product of two vec3s
 *
 * Params:
 * vec - vec3, first operand
 * vec2 - vec3, second operand
 *
 * Returns:
 * Dot product of vec and vec2
 */
vec3.dot = function(vec, vec2){
  return vec[0]*vec2[0] + vec[1]*vec2[1] + vec[2]*vec2[2];
};

/*
 * vec3.direction
 * Generates a unit vector pointing from one vector to another
 *
 * Params:
 * vec - origin vec3
 * vec2 - vec3 to point to
 * dest - Optional, vec3 receiving operation result. If not specified result is written to vec
 *
 * Returns:
 * dest if specified, vec otherwise
 */
vec3.direction = function(vec, vec2, dest) {
  if(!dest) { dest = vec; }
  
  var x = vec[0] - vec2[0];
  var y = vec[1] - vec2[1];
  var z = vec[2] - vec2[2];
  
  var len = Math.sqrt(x*x + y*y + z*z);
  if (!len) { 
    dest[0] = 0; 
    dest[1] = 0; 
    dest[2] = 0;
    return dest; 
  }
  
  len = 1 / len;
  dest[0] = x * len; 
  dest[1] = y * len; 
  dest[2] = z * len;
  return dest; 
};

/*
 * vec3.str
 * Returns a string representation of a vector
 *
 * Params:
 * vec - vec3 to represent as a string
 *
 * Returns:
 * string representation of vec
 */
vec3.str = function(vec) {
  return '[' + vec[0] + ', ' + vec[1] + ', ' + vec[2] + ']'; 
};

/*
 * mat3 - 3x3 Matrix
 */
var mat3 = {};

/*
 * mat3.create
 * Creates a new instance of a mat3 using the default array type
 * Any javascript array containing at least 9 numeric elements can serve as a mat3
 *
 * Params:
 * mat - Optional, mat3 containing values to initialize with
 *
 * Returns:
 * New mat3
 */
mat3.create = function(mat) {
  var dest = new glMatrixArrayType(9);
  
  if(mat) {
    dest[0] = mat[0];
    dest[1] = mat[1];
    dest[2] = mat[2];
    dest[3] = mat[3];
    dest[4] = mat[4];
    dest[5] = mat[5];
    dest[6] = mat[6];
    dest[7] = mat[7];
    dest[8] = mat[8];
    dest[9] = mat[9];
  }
  
  return dest;
};

/*
 * mat3.set
 * Copies the values of one mat3 to another
 *
 * Params:
 * mat - mat3 containing values to copy
 * dest - mat3 receiving copied values
 *
 * Returns:
 * dest
 */
mat3.set = function(mat, dest) {
  dest[0] = mat[0];
  dest[1] = mat[1];
  dest[2] = mat[2];
  dest[3] = mat[3];
  dest[4] = mat[4];
  dest[5] = mat[5];
  dest[6] = mat[6];
  dest[7] = mat[7];
  dest[8] = mat[8];
  return dest;
};

/*
 * mat3.identity
 * Sets a mat3 to an identity matrix
 *
 * Params:
 * dest - mat3 to set
 *
 * Returns:
 * dest
 */
mat3.identity = function(dest) {
  dest[0] = 1;
  dest[1] = 0;
  dest[2] = 0;
  dest[3] = 0;
  dest[4] = 1;
  dest[5] = 0;
  dest[6] = 0;
  dest[7] = 0;
  dest[8] = 1;
  return dest;
};

mat3.newIdentity = function() {
  return mat3.identity(mat3.create());
};

/*
 * mat3.toMat4
 * Copies the elements of a mat3 into the upper 3x3 elements of a mat4
 *
 * Params:
 * mat - mat3 containing values to copy
 * dest - Optional, mat4 receiving copied values
 *
 * Returns:
 * dest if specified, a new mat4 otherwise
 */
mat3.toMat4 = function(mat, dest) {
  if(!dest) { dest = mat4.create(); }
  
  dest[0] = mat[0];
  dest[1] = mat[1];
  dest[2] = mat[2];
  dest[3] = 0;

  dest[4] = mat[3];
  dest[5] = mat[4];
  dest[6] = mat[5];
  dest[7] = 0;

  dest[8] = mat[6];
  dest[9] = mat[7];
  dest[10] = mat[8];
  dest[11] = 0;

  dest[12] = 0;
  dest[13] = 0;
  dest[14] = 0;
  dest[15] = 1;
  
  return dest;
}

/*
 * mat3.transpose
 * Transposes a mat3 (flips the values over the diagonal)
 *
 * Params:
 * mat - mat3 to transpose
 * dest - Optional, mat3 receiving transposed values. If not specified result is written to mat
 *
 * Returns:
 * dest is specified, mat otherwise
 */
mat3.transpose = function(mat, dest) {
  // If we are transposing ourselves we can skip a few steps but have to cache some values
  if(!dest || mat == dest) {
    var m1 = mat[1], m2 = mat[2], m5 = mat[5];
    mat[1] = mat[3];
    mat[2] = mat[6];
    mat[5] = mat[7];
    mat[3] = m1;
    mat[6] = m2;
    mat[7] = m5;
    return mat;
  }
  
  dest[0] = mat[0];
  dest[1] = mat[3];
  dest[2] = mat[6];
  dest[3] = mat[1];
  dest[4] = mat[4];
  dest[5] = mat[7];
  dest[6] = mat[2];
  dest[7] = mat[5];
  dest[8] = mat[8];
  return dest;
};

/*
 * mat3.str
 * Returns a string representation of a mat3
 *
 * Params:
 * mat - mat3 to represent as a string
 *
 * Returns:
 * string representation of mat
 */
mat3.str = function(mat) {
  return '[' + mat[0] + ', ' + mat[1] + ', ' + mat[2] + 
    ', ' + mat[3] + ', '+ mat[4] + ', ' + mat[5] + 
    ', ' + mat[6] + ', ' + mat[7] + ', '+ mat[8] + ']';
};

/*
 * mat4 - 4x4 Matrix
 */
var mat4 = {};

/*
 * mat4.create
 * Creates a new instance of a mat4 using the default array type
 * Any javascript array containing at least 16 numeric elements can serve as a mat4
 *
 * Params:
 * mat - Optional, mat4 containing values to initialize with
 *
 * Returns:
 * New mat4
 */
mat4.create = function(mat) {
  var dest = new glMatrixArrayType(16);
  
  if(mat) {
    dest[0] = mat[0];
    dest[1] = mat[1];
    dest[2] = mat[2];
    dest[3] = mat[3];
    dest[4] = mat[4];
    dest[5] = mat[5];
    dest[6] = mat[6];
    dest[7] = mat[7];
    dest[8] = mat[8];
    dest[9] = mat[9];
    dest[10] = mat[10];
    dest[11] = mat[11];
    dest[12] = mat[12];
    dest[13] = mat[13];
    dest[14] = mat[14];
    dest[15] = mat[15];
  }
  
  return dest;
};

/*
 * mat4.set
 * Copies the values of one mat4 to another
 *
 * Params:
 * mat - mat4 containing values to copy
 * dest - mat4 receiving copied values
 *
 * Returns:
 * dest
 */
mat4.set = function(mat, dest) {
  dest[0] = mat[0];
  dest[1] = mat[1];
  dest[2] = mat[2];
  dest[3] = mat[3];
  dest[4] = mat[4];
  dest[5] = mat[5];
  dest[6] = mat[6];
  dest[7] = mat[7];
  dest[8] = mat[8];
  dest[9] = mat[9];
  dest[10] = mat[10];
  dest[11] = mat[11];
  dest[12] = mat[12];
  dest[13] = mat[13];
  dest[14] = mat[14];
  dest[15] = mat[15];
  return dest;
};

/*
 * mat4.identity
 * Sets a mat4 to an identity matrix
 *
 * Params:
 * dest - mat4 to set
 *
 * Returns:
 * dest
 */
mat4.identity = function(dest) {
  dest[0] = 1;
  dest[1] = 0;
  dest[2] = 0;
  dest[3] = 0;
  dest[4] = 0;
  dest[5] = 1;
  dest[6] = 0;
  dest[7] = 0;
  dest[8] = 0;
  dest[9] = 0;
  dest[10] = 1;
  dest[11] = 0;
  dest[12] = 0;
  dest[13] = 0;
  dest[14] = 0;
  dest[15] = 1;
  return dest;
};

mat4.newIdentity = function() {
  return mat4.identity(mat4.create());
};

/*
 * mat4.transpose
 * Transposes a mat4 (flips the values over the diagonal)
 *
 * Params:
 * mat - mat4 to transpose
 * dest - Optional, mat4 receiving transposed values. If not specified result is written to mat
 *
 * Returns:
 * dest is specified, mat otherwise
 */
mat4.transpose = function(mat, dest) {
  // If we are transposing ourselves we can skip a few steps but have to cache some values
  if(!dest || mat == dest) { 
    var a01 = mat[1], a02 = mat[2], a03 = mat[3];
    var a12 = mat[6], a13 = mat[7];
    var a23 = mat[11];
    
    mat[1] = mat[4];
    mat[2] = mat[8];
    mat[3] = mat[12];
    mat[4] = a01;
    mat[6] = mat[9];
    mat[7] = mat[13];
    mat[8] = a02;
    mat[9] = a12;
    mat[11] = mat[14];
    mat[12] = a03;
    mat[13] = a13;
    mat[14] = a23;
    return mat;
  }
  
  dest[0] = mat[0];
  dest[1] = mat[4];
  dest[2] = mat[8];
  dest[3] = mat[12];
  dest[4] = mat[1];
  dest[5] = mat[5];
  dest[6] = mat[9];
  dest[7] = mat[13];
  dest[8] = mat[2];
  dest[9] = mat[6];
  dest[10] = mat[10];
  dest[11] = mat[14];
  dest[12] = mat[3];
  dest[13] = mat[7];
  dest[14] = mat[11];
  dest[15] = mat[15];
  return dest;
};

/*
 * mat4.determinant
 * Calculates the determinant of a mat4
 *
 * Params:
 * mat - mat4 to calculate determinant of
 *
 * Returns:
 * determinant of mat
 */
mat4.determinant = function(mat) {
  // Cache the matrix values (makes for huge speed increases!)
  var a00 = mat[0], a01 = mat[1], a02 = mat[2], a03 = mat[3];
  var a10 = mat[4], a11 = mat[5], a12 = mat[6], a13 = mat[7];
  var a20 = mat[8], a21 = mat[9], a22 = mat[10], a23 = mat[11];
  var a30 = mat[12], a31 = mat[13], a32 = mat[14], a33 = mat[15];

  return  a30*a21*a12*a03 - a20*a31*a12*a03 - a30*a11*a22*a03 + a10*a31*a22*a03 +
      a20*a11*a32*a03 - a10*a21*a32*a03 - a30*a21*a02*a13 + a20*a31*a02*a13 +
      a30*a01*a22*a13 - a00*a31*a22*a13 - a20*a01*a32*a13 + a00*a21*a32*a13 +
      a30*a11*a02*a23 - a10*a31*a02*a23 - a30*a01*a12*a23 + a00*a31*a12*a23 +
      a10*a01*a32*a23 - a00*a11*a32*a23 - a20*a11*a02*a33 + a10*a21*a02*a33 +
      a20*a01*a12*a33 - a00*a21*a12*a33 - a10*a01*a22*a33 + a00*a11*a22*a33;
};

/*
 * mat4.inverse
 * Calculates the inverse matrix of a mat4
 *
 * Params:
 * mat - mat4 to calculate inverse of
 * dest - Optional, mat4 receiving inverse matrix. If not specified result is written to mat
 *
 * Returns:
 * dest is specified, mat otherwise
 */
mat4.inverse = function(mat, dest) {
  if(!dest) { dest = mat; }
  
  // Cache the matrix values (makes for huge speed increases!)
  var a00 = mat[0], a01 = mat[1], a02 = mat[2], a03 = mat[3];
  var a10 = mat[4], a11 = mat[5], a12 = mat[6], a13 = mat[7];
  var a20 = mat[8], a21 = mat[9], a22 = mat[10], a23 = mat[11];
  var a30 = mat[12], a31 = mat[13], a32 = mat[14], a33 = mat[15];
  
  var b00 = a00*a11 - a01*a10;
  var b01 = a00*a12 - a02*a10;
  var b02 = a00*a13 - a03*a10;
  var b03 = a01*a12 - a02*a11;
  var b04 = a01*a13 - a03*a11;
  var b05 = a02*a13 - a03*a12;
  var b06 = a20*a31 - a21*a30;
  var b07 = a20*a32 - a22*a30;
  var b08 = a20*a33 - a23*a30;
  var b09 = a21*a32 - a22*a31;
  var b10 = a21*a33 - a23*a31;
  var b11 = a22*a33 - a23*a32;
  
  // Calculate the determinant (inlined to avoid double-caching)
  var invDet = 1/(b00*b11 - b01*b10 + b02*b09 + b03*b08 - b04*b07 + b05*b06);
  
  dest[0] = (a11*b11 - a12*b10 + a13*b09)*invDet;
  dest[1] = (-a01*b11 + a02*b10 - a03*b09)*invDet;
  dest[2] = (a31*b05 - a32*b04 + a33*b03)*invDet;
  dest[3] = (-a21*b05 + a22*b04 - a23*b03)*invDet;
  dest[4] = (-a10*b11 + a12*b08 - a13*b07)*invDet;
  dest[5] = (a00*b11 - a02*b08 + a03*b07)*invDet;
  dest[6] = (-a30*b05 + a32*b02 - a33*b01)*invDet;
  dest[7] = (a20*b05 - a22*b02 + a23*b01)*invDet;
  dest[8] = (a10*b10 - a11*b08 + a13*b06)*invDet;
  dest[9] = (-a00*b10 + a01*b08 - a03*b06)*invDet;
  dest[10] = (a30*b04 - a31*b02 + a33*b00)*invDet;
  dest[11] = (-a20*b04 + a21*b02 - a23*b00)*invDet;
  dest[12] = (-a10*b09 + a11*b07 - a12*b06)*invDet;
  dest[13] = (a00*b09 - a01*b07 + a02*b06)*invDet;
  dest[14] = (-a30*b03 + a31*b01 - a32*b00)*invDet;
  dest[15] = (a20*b03 - a21*b01 + a22*b00)*invDet;
  
  return dest;
};

/*
 * mat4.toMat3
 * Copies the upper 3x3 elements of a mat4 into a mat3
 *
 * Params:
 * mat - mat4 containing values to copy
 * dest - Optional, mat3 receiving copied values
 *
 * Returns:
 * dest is specified, a new mat3 otherwise
 */
mat4.toMat3 = function(mat, dest) {
  if(!dest) { dest = mat3.create(); }
  
  dest[0] = mat[0];
  dest[1] = mat[1];
  dest[2] = mat[2];
  dest[3] = mat[4];
  dest[4] = mat[5];
  dest[5] = mat[6];
  dest[6] = mat[8];
  dest[7] = mat[9];
  dest[8] = mat[10];
  
  return dest;
};

/*
 * mat4.toInverseMat3
 * Calculates the inverse of the upper 3x3 elements of a mat4 and copies the result into a mat3
 * The resulting matrix is useful for calculating transformed normals
 *
 * Params:
 * mat - mat4 containing values to invert and copy
 * dest - Optional, mat3 receiving values
 *
 * Returns:
 * dest is specified, a new mat3 otherwise
 */
mat4.toInverseMat3 = function(mat, dest) {
  // Cache the matrix values (makes for huge speed increases!)
  var a00 = mat[0], a01 = mat[1], a02 = mat[2];
  var a10 = mat[4], a11 = mat[5], a12 = mat[6];
  var a20 = mat[8], a21 = mat[9], a22 = mat[10];
  
  var b01 = a22*a11-a12*a21;
  var b11 = -a22*a10+a12*a20;
  var b21 = a21*a10-a11*a20;
    
  var d = a00*b01 + a01*b11 + a02*b21;
  if (!d) { return null; }
  var id = 1/d;
  
  if(!dest) { dest = mat3.create(); }
  
  dest[0] = b01*id;
  dest[1] = (-a22*a01 + a02*a21)*id;
  dest[2] = (a12*a01 - a02*a11)*id;
  dest[3] = b11*id;
  dest[4] = (a22*a00 - a02*a20)*id;
  dest[5] = (-a12*a00 + a02*a10)*id;
  dest[6] = b21*id;
  dest[7] = (-a21*a00 + a01*a20)*id;
  dest[8] = (a11*a00 - a01*a10)*id;
  
  return dest;
};

/*
 * mat4.multiply
 * Performs a matrix multiplication
 *
 * Params:
 * mat - mat4, first operand
 * mat2 - mat4, second operand
 * dest - Optional, mat4 receiving operation result. If not specified result is written to mat
 *
 * Returns:
 * dest if specified, mat otherwise
 */
mat4.multiply = function(mat, mat2, dest) {
  if(!dest) { dest = mat }
  
  // Cache the matrix values (makes for huge speed increases!)
  var a00 = mat[0], a01 = mat[1], a02 = mat[2], a03 = mat[3];
  var a10 = mat[4], a11 = mat[5], a12 = mat[6], a13 = mat[7];
  var a20 = mat[8], a21 = mat[9], a22 = mat[10], a23 = mat[11];
  var a30 = mat[12], a31 = mat[13], a32 = mat[14], a33 = mat[15];
  
  var b00 = mat2[0], b01 = mat2[1], b02 = mat2[2], b03 = mat2[3];
  var b10 = mat2[4], b11 = mat2[5], b12 = mat2[6], b13 = mat2[7];
  var b20 = mat2[8], b21 = mat2[9], b22 = mat2[10], b23 = mat2[11];
  var b30 = mat2[12], b31 = mat2[13], b32 = mat2[14], b33 = mat2[15];
  
  dest[0] = b00*a00 + b01*a10 + b02*a20 + b03*a30;
  dest[1] = b00*a01 + b01*a11 + b02*a21 + b03*a31;
  dest[2] = b00*a02 + b01*a12 + b02*a22 + b03*a32;
  dest[3] = b00*a03 + b01*a13 + b02*a23 + b03*a33;
  dest[4] = b10*a00 + b11*a10 + b12*a20 + b13*a30;
  dest[5] = b10*a01 + b11*a11 + b12*a21 + b13*a31;
  dest[6] = b10*a02 + b11*a12 + b12*a22 + b13*a32;
  dest[7] = b10*a03 + b11*a13 + b12*a23 + b13*a33;
  dest[8] = b20*a00 + b21*a10 + b22*a20 + b23*a30;
  dest[9] = b20*a01 + b21*a11 + b22*a21 + b23*a31;
  dest[10] = b20*a02 + b21*a12 + b22*a22 + b23*a32;
  dest[11] = b20*a03 + b21*a13 + b22*a23 + b23*a33;
  dest[12] = b30*a00 + b31*a10 + b32*a20 + b33*a30;
  dest[13] = b30*a01 + b31*a11 + b32*a21 + b33*a31;
  dest[14] = b30*a02 + b31*a12 + b32*a22 + b33*a32;
  dest[15] = b30*a03 + b31*a13 + b32*a23 + b33*a33;
  
  return dest;
};

/*
 * mat4.multiplyVec3
 * Transforms a vec3 with the given matrix
 * 4th vector component is implicitly '1'
 *
 * Params:
 * mat - mat4 to transform the vector with
 * vec - vec3 to transform
 * dest - Optional, vec3 receiving operation result. If not specified result is written to vec
 *
 * Returns:
 * dest if specified, vec otherwise
 */
mat4.multiplyVec3 = function(mat, vec, dest) {
  if(!dest) { dest = vec }
  
  var x = vec[0], y = vec[1], z = vec[2];
  
  dest[0] = mat[0]*x + mat[4]*y + mat[8]*z + mat[12];
  dest[1] = mat[1]*x + mat[5]*y + mat[9]*z + mat[13];
  dest[2] = mat[2]*x + mat[6]*y + mat[10]*z + mat[14];
  
  return dest;
};

/*
 * mat4.multiplyVec4
 * Transforms a vec4 with the given matrix
 *
 * Params:
 * mat - mat4 to transform the vector with
 * vec - vec4 to transform
 * dest - Optional, vec4 receiving operation result. If not specified result is written to vec
 *
 * Returns:
 * dest if specified, vec otherwise
 */
mat4.multiplyVec4 = function(mat, vec, dest) {
  if(!dest) { dest = vec }
  
  var x = vec[0], y = vec[1], z = vec[2], w = vec[3];
  
  dest[0] = mat[0]*x + mat[4]*y + mat[8]*z + mat[12]*w;
  dest[1] = mat[1]*x + mat[5]*y + mat[9]*z + mat[13]*w;
  dest[2] = mat[2]*x + mat[6]*y + mat[10]*z + mat[14]*w;
  dest[4] = mat[4]*x + mat[7]*y + mat[11]*z + mat[15]*w;
  
  return dest;
};

/*
 * mat4.translate
 * Translates a matrix by the given vector
 *
 * Params:
 * mat - mat4 to translate
 * vec - vec3 specifying the translation
 * dest - Optional, mat4 receiving operation result. If not specified result is written to mat
 *
 * Returns:
 * dest if specified, mat otherwise
 */
mat4.translate = function(mat, vec, dest) {
  var x = vec[0], y = vec[1], z = vec[2];
  
  if(!dest || mat == dest) {
    mat[12] = mat[0]*x + mat[4]*y + mat[8]*z + mat[12];
    mat[13] = mat[1]*x + mat[5]*y + mat[9]*z + mat[13];
    mat[14] = mat[2]*x + mat[6]*y + mat[10]*z + mat[14];
    mat[15] = mat[3]*x + mat[7]*y + mat[11]*z + mat[15];
    return mat;
  }
  
  var a00 = mat[0], a01 = mat[1], a02 = mat[2], a03 = mat[3];
  var a10 = mat[4], a11 = mat[5], a12 = mat[6], a13 = mat[7];
  var a20 = mat[8], a21 = mat[9], a22 = mat[10], a23 = mat[11];
  
  dest[0] = a00;
  dest[1] = a01;
  dest[2] = a02;
  dest[3] = a03;
  dest[4] = a10;
  dest[5] = a11;
  dest[6] = a12;
  dest[7] = a13;
  dest[8] = a20;
  dest[9] = a21;
  dest[10] = a22;
  dest[11] = a23;
  
  dest[12] = a00*x + a10*y + a20*z + mat[12];
  dest[13] = a01*x + a11*y + a21*z + mat[13];
  dest[14] = a02*x + a12*y + a22*z + mat[14];
  dest[15] = a03*x + a13*y + a23*z + mat[15];
  return dest;
};

/*
 * mat4.scale
 * Scales a matrix by the given vector
 *
 * Params:
 * mat - mat4 to scale
 * vec - vec3 specifying the scale for each axis
 * dest - Optional, mat4 receiving operation result. If not specified result is written to mat
 *
 * Returns:
 * dest if specified, mat otherwise
 */
mat4.scale = function(mat, vec, dest) {
  var x = vec[0], y = vec[1], z = vec[2];
  
  if(!dest || mat === dest) {
    mat[0] *= x;
    mat[1] *= x;
    mat[2] *= x;
    mat[3] *= x;
    mat[4] *= y;
    mat[5] *= y;
    mat[6] *= y;
    mat[7] *= y;
    mat[8] *= z;
    mat[9] *= z;
    mat[10] *= z;
    mat[11] *= z;
    return mat;
  }
  
  dest[0] = mat[0]*x;
  dest[1] = mat[1]*x;
  dest[2] = mat[2]*x;
  dest[3] = mat[3]*x;
  dest[4] = mat[4]*y;
  dest[5] = mat[5]*y;
  dest[6] = mat[6]*y;
  dest[7] = mat[7]*y;
  dest[8] = mat[8]*z;
  dest[9] = mat[9]*z;
  dest[10] = mat[10]*z;
  dest[11] = mat[11]*z;
  dest[12] = mat[12];
  dest[13] = mat[13];
  dest[14] = mat[14];
  dest[15] = mat[15];
  return dest;
};

mat4.billboard = function(mat, dest) {
  var a = mat[0], b = mat[5], c = mat[10];
  a = a*a; b = b*b; c = c*c;
  var sc = a > b ? a : b;
  sc = sc > c ? sc : c;
  sc = Math.sqrt(sc);
  // this should really do rotate mat3 = inverse of view matrix rotate mat3
  if (!dest || mat === dest) {
    mat[0] = sc;
    mat[1] = 0;
    mat[2] = 0;
    mat[4] = 0;
    mat[5] = sc;
    mat[6] = 0;
    mat[8] = 0;
    mat[9] = 0;
    mat[10] = sc;
    return mat;
  }
  dest[0] = sc;
  dest[1] = 0;
  dest[2] = 0;
  dest[3] = mat[3];
  dest[4] = 0;
  dest[5] = sc;
  dest[6] = 0;
  dest[7] = mat[7];
  dest[8] = 0;
  dest[9] = 0;
  dest[10] = sc;
  dest[11] = mat[11];
  dest[12] = mat[12];
  dest[13] = mat[13];
  dest[14] = mat[14];
  dest[15] = mat[15];
  return dest;
};

/*
 * mat4.rotate
 * Rotates a matrix by the given angle around the specified axis
 * If rotating around a primary axis (X,Y,Z) one of the specialized rotation functions should be used instead for performance
 *
 * Params:
 * mat - mat4 to rotate
 * angle - angle (in radians) to rotate
 * axis - vec3 representing the axis to rotate around 
 * dest - Optional, mat4 receiving operation result. If not specified result is written to mat
 *
 * Returns:
 * dest if specified, mat otherwise
 */
mat4.rotate = function(mat, angle, axis, dest) {
  var x = axis[0], y = axis[1], z = axis[2];
  var len = Math.sqrt(x*x + y*y + z*z);
  if (!len) { return null; }
  if (len != 1) {
    len = 1 / len;
    x *= len; 
    y *= len; 
    z *= len;
  }
  
  var s = Math.sin(angle);
  var c = Math.cos(angle);
  var t = 1-c;
  
  // Cache the matrix values (makes for huge speed increases!)
  var a00 = mat[0], a01 = mat[1], a02 = mat[2], a03 = mat[3];
  var a10 = mat[4], a11 = mat[5], a12 = mat[6], a13 = mat[7];
  var a20 = mat[8], a21 = mat[9], a22 = mat[10], a23 = mat[11];
  
  // Construct the elements of the rotation matrix
  var b00 = x*x*t + c, b01 = y*x*t + z*s, b02 = z*x*t - y*s;
  var b10 = x*y*t - z*s, b11 = y*y*t + c, b12 = z*y*t + x*s;
  var b20 = x*z*t + y*s, b21 = y*z*t - x*s, b22 = z*z*t + c;
  
  if(!dest) { 
    dest = mat 
  } else if(mat != dest) { // If the source and destination differ, copy the unchanged last row
    dest[12] = mat[12];
    dest[13] = mat[13];
    dest[14] = mat[14];
    dest[15] = mat[15];
  }
  
  // Perform rotation-specific matrix multiplication
  dest[0] = a00*b00 + a10*b01 + a20*b02;
  dest[1] = a01*b00 + a11*b01 + a21*b02;
  dest[2] = a02*b00 + a12*b01 + a22*b02;
  dest[3] = a03*b00 + a13*b01 + a23*b02;
  
  dest[4] = a00*b10 + a10*b11 + a20*b12;
  dest[5] = a01*b10 + a11*b11 + a21*b12;
  dest[6] = a02*b10 + a12*b11 + a22*b12;
  dest[7] = a03*b10 + a13*b11 + a23*b12;
  
  dest[8] = a00*b20 + a10*b21 + a20*b22;
  dest[9] = a01*b20 + a11*b21 + a21*b22;
  dest[10] = a02*b20 + a12*b21 + a22*b22;
  dest[11] = a03*b20 + a13*b21 + a23*b22;
  return dest;
};

/*
 * mat4.rotateX
 * Rotates a matrix by the given angle around the X axis
 *
 * Params:
 * mat - mat4 to rotate
 * angle - angle (in radians) to rotate
 * dest - Optional, mat4 receiving operation result. If not specified result is written to mat
 *
 * Returns:
 * dest if specified, mat otherwise
 */
mat4.rotateX = function(mat, angle, dest) {
  var s = Math.sin(angle);
  var c = Math.cos(angle);
  
  // Cache the matrix values (makes for huge speed increases!)
  var a10 = mat[4], a11 = mat[5], a12 = mat[6], a13 = mat[7];
  var a20 = mat[8], a21 = mat[9], a22 = mat[10], a23 = mat[11];

  if(!dest) { 
    dest = mat 
  } else if(mat != dest) { // If the source and destination differ, copy the unchanged rows
    dest[0] = mat[0];
    dest[1] = mat[1];
    dest[2] = mat[2];
    dest[3] = mat[3];
    
    dest[12] = mat[12];
    dest[13] = mat[13];
    dest[14] = mat[14];
    dest[15] = mat[15];
  }
  
  // Perform axis-specific matrix multiplication
  dest[4] = a10*c + a20*s;
  dest[5] = a11*c + a21*s;
  dest[6] = a12*c + a22*s;
  dest[7] = a13*c + a23*s;
  
  dest[8] = a10*-s + a20*c;
  dest[9] = a11*-s + a21*c;
  dest[10] = a12*-s + a22*c;
  dest[11] = a13*-s + a23*c;
  return dest;
};

/*
 * mat4.rotateY
 * Rotates a matrix by the given angle around the Y axis
 *
 * Params:
 * mat - mat4 to rotate
 * angle - angle (in radians) to rotate
 * dest - Optional, mat4 receiving operation result. If not specified result is written to mat
 *
 * Returns:
 * dest if specified, mat otherwise
 */
mat4.rotateY = function(mat, angle, dest) {
  var s = Math.sin(angle);
  var c = Math.cos(angle);
  
  // Cache the matrix values (makes for huge speed increases!)
  var a00 = mat[0], a01 = mat[1], a02 = mat[2], a03 = mat[3];
  var a20 = mat[8], a21 = mat[9], a22 = mat[10], a23 = mat[11];
  
  if(!dest) { 
    dest = mat 
  } else if(mat != dest) { // If the source and destination differ, copy the unchanged rows
    dest[4] = mat[4];
    dest[5] = mat[5];
    dest[6] = mat[6];
    dest[7] = mat[7];
    
    dest[12] = mat[12];
    dest[13] = mat[13];
    dest[14] = mat[14];
    dest[15] = mat[15];
  }
  
  // Perform axis-specific matrix multiplication
  dest[0] = a00*c + a20*-s;
  dest[1] = a01*c + a21*-s;
  dest[2] = a02*c + a22*-s;
  dest[3] = a03*c + a23*-s;
  
  dest[8] = a00*s + a20*c;
  dest[9] = a01*s + a21*c;
  dest[10] = a02*s + a22*c;
  dest[11] = a03*s + a23*c;
  return dest;
};

/*
 * mat4.rotateZ
 * Rotates a matrix by the given angle around the Z axis
 *
 * Params:
 * mat - mat4 to rotate
 * angle - angle (in radians) to rotate
 * dest - Optional, mat4 receiving operation result. If not specified result is written to mat
 *
 * Returns:
 * dest if specified, mat otherwise
 */
mat4.rotateZ = function(mat, angle, dest) {
  var s = Math.sin(angle);
  var c = Math.cos(angle);
  
  // Cache the matrix values (makes for huge speed increases!)
  var a00 = mat[0], a01 = mat[1], a02 = mat[2], a03 = mat[3];
  var a10 = mat[4], a11 = mat[5], a12 = mat[6], a13 = mat[7];
  
  if(!dest) { 
    dest = mat 
  } else if(mat != dest) { // If the source and destination differ, copy the unchanged last row
    dest[8] = mat[8];
    dest[9] = mat[9];
    dest[10] = mat[10];
    dest[11] = mat[11];
    
    dest[12] = mat[12];
    dest[13] = mat[13];
    dest[14] = mat[14];
    dest[15] = mat[15];
  }
  
  // Perform axis-specific matrix multiplication
  dest[0] = a00*c + a10*s;
  dest[1] = a01*c + a11*s;
  dest[2] = a02*c + a12*s;
  dest[3] = a03*c + a13*s;
  
  dest[4] = a00*-s + a10*c;
  dest[5] = a01*-s + a11*c;
  dest[6] = a02*-s + a12*c;
  dest[7] = a03*-s + a13*c;
  
  return dest;
};

/*
 * mat4.frustum
 * Generates a frustum matrix with the given bounds
 *
 * Params:
 * left, right - scalar, left and right bounds of the frustum
 * bottom, top - scalar, bottom and top bounds of the frustum
 * near, far - scalar, near and far bounds of the frustum
 * dest - Optional, mat4 frustum matrix will be written into
 *
 * Returns:
 * dest if specified, a new mat4 otherwise
 */
mat4.frustum = function(left, right, bottom, top, near, far, dest) {
  if(!dest) { dest = mat4.create(); }
  var rl = (right - left);
  var tb = (top - bottom);
  var fn = (far - near);
  dest[0] = (near*2) / rl;
  dest[1] = 0;
  dest[2] = 0;
  dest[3] = 0;
  dest[4] = 0;
  dest[5] = (near*2) / tb;
  dest[6] = 0;
  dest[7] = 0;
  dest[8] = (right + left) / rl;
  dest[9] = (top + bottom) / tb;
  dest[10] = -(far + near) / fn;
  dest[11] = -1;
  dest[12] = 0;
  dest[13] = 0;
  dest[14] = -(far*near*2) / fn;
  dest[15] = 0;
  return dest;
};

/*
 * mat4.perspective
 * Generates a perspective projection matrix with the given bounds
 *
 * Params:
 * fovy - scalar, vertical field of view
 * aspect - scalar, aspect ratio. typically viewport width/height
 * near, far - scalar, near and far bounds of the frustum
 * dest - Optional, mat4 frustum matrix will be written into
 *
 * Returns:
 * dest if specified, a new mat4 otherwise
 */
mat4.perspective = function(fovy, aspect, near, far, dest) {
  var top = near*Math.tan(fovy*Math.PI / 360.0);
  var right = top*aspect;
  return mat4.frustum(-right, right, -top, top, near, far, dest);
};

/*
 * mat4.ortho
 * Generates a orthogonal projection matrix with the given bounds
 *
 * Params:
 * left, right - scalar, left and right bounds of the frustum
 * bottom, top - scalar, bottom and top bounds of the frustum
 * near, far - scalar, near and far bounds of the frustum
 * dest - Optional, mat4 frustum matrix will be written into
 *
 * Returns:
 * dest if specified, a new mat4 otherwise
 */
mat4.ortho = function(left, right, bottom, top, near, far, dest) {
  if(!dest) { dest = mat4.create(); }
  var rl = (right - left);
  var tb = (top - bottom);
  var fn = (far - near);
  dest[0] = 2 / rl;
  dest[1] = 0;
  dest[2] = 0;
  dest[3] = 0;
  dest[4] = 0;
  dest[5] = 2 / tb;
  dest[6] = 0;
  dest[7] = 0;
  dest[8] = 0;
  dest[9] = 0;
  dest[10] = -2 / fn;
  dest[11] = 0;
  dest[12] = (left + right) / rl;
  dest[13] = (top + bottom) / tb;
  dest[14] = (far + near) / fn;
  dest[15] = 1;
  return dest;
};

/*
 * mat4.ortho
 * Generates a look-at matrix with the given eye position, focal point, and up axis
 *
 * Params:
 * eye - vec3, position of the viewer
 * center - vec3, point the viewer is looking at
 * up - vec3 pointing "up"
 * dest - Optional, mat4 frustum matrix will be written into
 *
 * Returns:
 * dest if specified, a new mat4 otherwise
 */
mat4.lookAt = function(eye, center, up, dest) {
  if(!dest) { dest = mat4.create(); }
  
  var eyex = eye[0],
    eyey = eye[1],
    eyez = eye[2],
    upx = up[0],
    upy = up[1],
    upz = up[2],
    centerx = center[0],
    centery = center[1],
    centerz = center[2];

  if (eyex == centerx && eyey == centery && eyez == centerz) {
    return mat4.identity(dest);
  }
  
  var z0,z1,z2,x0,x1,x2,y0,y1,y2,len;
  
  //vec3.direction(eye, center, z);
  z0 = eyex - center[0];
  z1 = eyey - center[1];
  z2 = eyez - center[2];
  
  // normalize (no check needed for 0 because of early return)
  len = 1/Math.sqrt(z0*z0 + z1*z1 + z2*z2);
  z0 *= len;
  z1 *= len;
  z2 *= len;
  
  //vec3.normalize(vec3.cross(up, z, x));
  x0 = upy*z2 - upz*z1;
  x1 = upz*z0 - upx*z2;
  x2 = upx*z1 - upy*z0;
  len = Math.sqrt(x0*x0 + x1*x1 + x2*x2);
  if (!len) {
    x0 = 0;
    x1 = 0;
    x2 = 0;
  } else {
    len = 1/len;
    x0 *= len;
    x1 *= len;
    x2 *= len;
  };
  
  //vec3.normalize(vec3.cross(z, x, y));
  y0 = z1*x2 - z2*x1;
  y1 = z2*x0 - z0*x2;
  y2 = z0*x1 - z1*x0;
  
  len = Math.sqrt(y0*y0 + y1*y1 + y2*y2);
  if (!len) {
    y0 = 0;
    y1 = 0;
    y2 = 0;
  } else {
    len = 1/len;
    y0 *= len;
    y1 *= len;
    y2 *= len;
  }
  
  dest[0] = x0;
  dest[1] = y0;
  dest[2] = z0;
  dest[3] = 0;
  dest[4] = x1;
  dest[5] = y1;
  dest[6] = z1;
  dest[7] = 0;
  dest[8] = x2;
  dest[9] = y2;
  dest[10] = z2;
  dest[11] = 0;
  dest[12] = -(x0*eyex + x1*eyey + x2*eyez);
  dest[13] = -(y0*eyex + y1*eyey + y2*eyez);
  dest[14] = -(z0*eyex + z1*eyey + z2*eyez);
  dest[15] = 1;
  
  return dest;
};

/*
 * mat4.str
 * Returns a string representation of a mat4
 *
 * Params:
 * mat - mat4 to represent as a string
 *
 * Returns:
 * string representation of mat
 */
mat4.str = function(mat) {
  return '[' + mat[0] + ', ' + mat[1] + ', ' + mat[2] + ', ' + mat[3] + 
    ', '+ mat[4] + ', ' + mat[5] + ', ' + mat[6] + ', ' + mat[7] + 
    ', '+ mat[8] + ', ' + mat[9] + ', ' + mat[10] + ', ' + mat[11] + 
    ', '+ mat[12] + ', ' + mat[13] + ', ' + mat[14] + ', ' + mat[15] + ']';
};

/*
 * quat4 - Quaternions 
 */
quat4 = {};

/*
 * quat4.create
 * Creates a new instance of a quat4 using the default array type
 * Any javascript array containing at least 4 numeric elements can serve as a quat4
 *
 * Params:
 * quat - Optional, quat4 containing values to initialize with
 *
 * Returns:
 * New quat4
 */
quat4.create = function(quat) {
  var dest = new glMatrixArrayType(4);
  
  if(quat) {
    dest[0] = quat[0];
    dest[1] = quat[1];
    dest[2] = quat[2];
    dest[3] = quat[3];
  }
  
  return dest;
};

/*
 * quat4.set
 * Copies the values of one quat4 to another
 *
 * Params:
 * quat - quat4 containing values to copy
 * dest - quat4 receiving copied values
 *
 * Returns:
 * dest
 */
quat4.set = function(quat, dest) {
  dest[0] = quat[0];
  dest[1] = quat[1];
  dest[2] = quat[2];
  dest[3] = quat[3];
  
  return dest;
};

/*
 * quat4.calculateW
 * Calculates the W component of a quat4 from the X, Y, and Z components.
 * Assumes that quaternion is 1 unit in length. 
 * Any existing W component will be ignored. 
 *
 * Params:
 * quat - quat4 to calculate W component of
 * dest - Optional, quat4 receiving calculated values. If not specified result is written to quat
 *
 * Returns:
 * dest if specified, quat otherwise
 */
quat4.calculateW = function(quat, dest) {
  var x = quat[0], y = quat[1], z = quat[2];

  if(!dest || quat == dest) {
    quat[3] = -Math.sqrt(Math.abs(1.0 - x*x - y*y - z*z));
    return quat;
  }
  dest[0] = x;
  dest[1] = y;
  dest[2] = z;
  dest[3] = -Math.sqrt(Math.abs(1.0 - x*x - y*y - z*z));
  return dest;
}

/*
 * quat4.inverse
 * Calculates the inverse of a quat4
 *
 * Params:
 * quat - quat4 to calculate inverse of
 * dest - Optional, quat4 receiving inverse values. If not specified result is written to quat
 *
 * Returns:
 * dest if specified, quat otherwise
 */
quat4.inverse = function(quat, dest) {
  if(!dest || quat == dest) {
    quat[0] *= 1;
    quat[1] *= 1;
    quat[2] *= 1;
    return quat;
  }
  dest[0] = -quat[0];
  dest[1] = -quat[1];
  dest[2] = -quat[2];
  dest[3] = quat[3];
  return dest;
}

/*
 * quat4.length
 * Calculates the length of a quat4
 *
 * Params:
 * quat - quat4 to calculate length of
 *
 * Returns:
 * Length of quat
 */
quat4.length = function(quat) {
  var x = quat[0], y = quat[1], z = quat[2], w = quat[3];
  return Math.sqrt(x*x + y*y + z*z + w*w);
}

/*
 * quat4.normalize
 * Generates a unit quaternion of the same direction as the provided quat4
 * If quaternion length is 0, returns [0, 0, 0, 0]
 *
 * Params:
 * quat - quat4 to normalize
 * dest - Optional, quat4 receiving operation result. If not specified result is written to quat
 *
 * Returns:
 * dest if specified, quat otherwise
 */
quat4.normalize = function(quat, dest) {
  if(!dest) { dest = quat; }
  
  var x = quat[0], y = quat[1], z = quat[2], w = quat[3];
  var len = Math.sqrt(x*x + y*y + z*z + w*w);
  if(len == 0) {
    dest[0] = 0;
    dest[1] = 0;
    dest[2] = 0;
    dest[3] = 0;
    return dest;
  }
  len = 1/len;
  dest[0] = x * len;
  dest[1] = y * len;
  dest[2] = z * len;
  dest[3] = w * len;
  
  return dest;
}

/*
 * quat4.multiply
 * Performs a quaternion multiplication
 *
 * Params:
 * quat - quat4, first operand
 * quat2 - quat4, second operand
 * dest - Optional, quat4 receiving operation result. If not specified result is written to quat
 *
 * Returns:
 * dest if specified, quat otherwise
 */
quat4.multiply = function(quat, quat2, dest) {
  if(!dest) { dest = quat; }
  
  var qax = quat[0], qay = quat[1], qaz = quat[2], qaw = quat[3];
  var qbx = quat2[0], qby = quat2[1], qbz = quat2[2], qbw = quat2[3];
  
  dest[0] = qax*qbw + qaw*qbx + qay*qbz - qaz*qby;
  dest[1] = qay*qbw + qaw*qby + qaz*qbx - qax*qbz;
  dest[2] = qaz*qbw + qaw*qbz + qax*qby - qay*qbx;
  dest[3] = qaw*qbw - qax*qbx - qay*qby - qaz*qbz;
  
  return dest;
}

/*
 * quat4.multiplyVec3
 * Transforms a vec3 with the given quaternion
 *
 * Params:
 * quat - quat4 to transform the vector with
 * vec - vec3 to transform
 * dest - Optional, vec3 receiving operation result. If not specified result is written to vec
 *
 * Returns:
 * dest if specified, vec otherwise
 */
quat4.multiplyVec3 = function(quat, vec, dest) {
  if(!dest) { dest = vec; }
  
  var x = vec[0], y = vec[1], z = vec[2];
  var qx = quat[0], qy = quat[1], qz = quat[2], qw = quat[3];

  // calculate quat * vec
  var ix = qw*x + qy*z - qz*y;
  var iy = qw*y + qz*x - qx*z;
  var iz = qw*z + qx*y - qy*x;
  var iw = -qx*x - qy*y - qz*z;
  
  // calculate result * inverse quat
  dest[0] = ix*qw + iw*-qx + iy*-qz - iz*-qy;
  dest[1] = iy*qw + iw*-qy + iz*-qx - ix*-qz;
  dest[2] = iz*qw + iw*-qz + ix*-qy - iy*-qx;
  
  return dest;
}

/*
 * quat4.toMat3
 * Calculates a 3x3 matrix from the given quat4
 *
 * Params:
 * quat - quat4 to create matrix from
 * dest - Optional, mat3 receiving operation result
 *
 * Returns:
 * dest if specified, a new mat3 otherwise
 */
quat4.toMat3 = function(quat, dest) {
  if(!dest) { dest = mat3.create(); }
  
  var x = quat[0], y = quat[1], z = quat[2], w = quat[3];

  var x2 = x + x;
  var y2 = y + y;
  var z2 = z + z;

  var xx = x*x2;
  var xy = x*y2;
  var xz = x*z2;

  var yy = y*y2;
  var yz = y*z2;
  var zz = z*z2;

  var wx = w*x2;
  var wy = w*y2;
  var wz = w*z2;

  dest[0] = 1 - (yy + zz);
  dest[1] = xy - wz;
  dest[2] = xz + wy;

  dest[3] = xy + wz;
  dest[4] = 1 - (xx + zz);
  dest[5] = yz - wx;

  dest[6] = xz - wy;
  dest[7] = yz + wx;
  dest[8] = 1 - (xx + yy);
  
  return dest;
}

/*
 * quat4.toMat4
 * Calculates a 4x4 matrix from the given quat4
 *
 * Params:
 * quat - quat4 to create matrix from
 * dest - Optional, mat4 receiving operation result
 *
 * Returns:
 * dest if specified, a new mat4 otherwise
 */
quat4.toMat4 = function(quat, dest) {
  if(!dest) { dest = mat4.create(); }
  
  var x = quat[0], y = quat[1], z = quat[2], w = quat[3];

  var x2 = x + x;
  var y2 = y + y;
  var z2 = z + z;

  var xx = x*x2;
  var xy = x*y2;
  var xz = x*z2;

  var yy = y*y2;
  var yz = y*z2;
  var zz = z*z2;

  var wx = w*x2;
  var wy = w*y2;
  var wz = w*z2;

  dest[0] = 1 - (yy + zz);
  dest[1] = xy - wz;
  dest[2] = xz + wy;
  dest[3] = 0;

  dest[4] = xy + wz;
  dest[5] = 1 - (xx + zz);
  dest[6] = yz - wx;
  dest[7] = 0;

  dest[8] = xz - wy;
  dest[9] = yz + wx;
  dest[10] = 1 - (xx + yy);
  dest[11] = 0;

  dest[12] = 0;
  dest[13] = 0;
  dest[14] = 0;
  dest[15] = 1;
  
  return dest;
}

/*
 * quat4.str
 * Returns a string representation of a quaternion
 *
 * Params:
 * quat - quat4 to represent as a string
 *
 * Returns:
 * string representation of quat
 */
quat4.str = function(quat) {
  return '[' + quat[0] + ', ' + quat[1] + ', ' + quat[2] + ', ' + quat[3] + ']'; 
};


//GL_UTIL/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

if (typeof Magi == 'undefined') Magi = {};

if (!window['$A']) {
  /**
    Creates a new array from an object with #length.
    */
  $A = function(obj) {
    var a = new Array(obj.length)
    for (var i=0; i<obj.length; i++)
      a[i] = obj[i]
    return a
  }
}

/**
  Merges the src object's attributes with the dst object, ignoring errors.

  @param dst The destination object
  @param src The source object
  @return The dst object
  @addon
  */
Object.forceExtend = function(dst, src) {
  for (var i in src) {
    try{ dst[i] = src[i] } catch(e) {}
  }
  return dst
}
// In case Object.extend isn't defined already, set it to Object.forceExtend.
if (!Object.extend)
  Object.extend = Object.forceExtend

/**
  Klass is a function that returns a constructor function.

  The constructor function calls #initialize with its arguments.

  The parameters to Klass have their prototypes or themselves merged with the
  constructor function's prototype.

  Finally, the constructor function's prototype is merged with the constructor
  function. So you can write Shape.getArea.call(this) instead of
  Shape.prototype.getArea.call(this).

  Shape = Klass({
    getArea : function() {
      raise('No area defined!')
    }
  })

  Rectangle = Klass(Shape, {
    initialize : function(x, y) {
      this.x = x
      this.y = y
    },

    getArea : function() {
      return this.x * this.y
    }
  })

  Square = Klass(Rectangle, {
    initialize : function(s) {
      Rectangle.initialize.call(this, s, s)
    }
  })

  new Square(5).getArea()
  //=> 25

  @return Constructor object for the class
  */
Klass = function() {
  var c = function() {
    this.initialize.apply(this, arguments)
  }
  c.ancestors = $A(arguments)
  c.prototype = {}
  for(var i = 0; i<arguments.length; i++) {
    var a = arguments[i]
    if (a.prototype) {
      Object.extend(c.prototype, a.prototype)
    } else {
      Object.extend(c.prototype, a)
    }
  }
  Object.extend(c, c.prototype)
  return c
}


Magi.checkError = function(gl, msg) {
  var e = gl.getError();
  if (e != 0) {
    Magi.log("Error " + e + " at " + msg);
  }
  return e;
}

Magi.throwError = function(gl, msg) {
  var e = gl.getError();
  if (e != 0) {
    throw(new Error("Error " + e + " at " + msg));
  }
}

Magi.AllocatedResources = {
  textures : [],
  vbos : [],
  shaders : [],
  fbos : [],

  deleteAll : function() {
    while (this.textures.length > 0)
      this.textures[0].destroy();
    while (this.vbos.length > 0)
      this.vbos[0].destroy();
    while (this.fbos.length > 0)
      this.fbos[0].destroy();
    while (this.shaders.length > 0)
      this.shaders[0].destroy();
  },
  
  hideAll : function() {
	  this.textures[0].destroy();
	  this.vbos[0].destroy();
	 // this.shaders[0].destroy();
  },
  
  

  addTexture : function(tex) {
    if (this.textures.indexOf(tex) == -1)
      this.textures.push(tex);
  },

  addShader : function(tex) {
    if (this.shaders.indexOf(tex) == -1)
      this.shaders.push(tex);
  },

  addVBO : function(tex) {
    if (this.vbos.indexOf(tex) == -1)
      this.vbos.push(tex);
  },

  addFBO : function(tex) {
    if (this.fbos.indexOf(tex) == -1)
      this.fbos.push(tex);
  },

  deleteTexture : function(tex) {
    var i = this.textures.indexOf(tex);
    if (i >= 0)
      this.textures.splice(i,1);
  },

  deleteShader : function(tex) {
    var i = this.shaders.indexOf(tex);
    if (i >= 0)
      this.shaders.splice(i,1);
  },

  deleteVBO : function(tex) {
    var i = this.vbos.indexOf(tex);
    if (i >= 0)
      this.vbos.splice(i,1);
  },

  deleteFBO : function(tex) {
    var i = this.fbos.indexOf(tex);
    if (i >= 0)
      this.fbos.splice(i,1);
  }
};

window.onunload = function(){ Magi.AllocatedResources.deleteAll(); };

Magi.Texture = Klass({
  target : 'TEXTURE_2D',
  generateMipmaps : true,
  width : null,
  height : null,
  data : null,
  changed : false,

  initialize : function(gl) {
    this.gl = gl;
    Magi.AllocatedResources.addTexture(this);
  },

  defaultTexCache : {},
  getDefaultTexture : function(gl) {
    if (!this.defaultTexCache[gl]) {
      var tex = new this(gl);
      tex.image = E.canvas(1,1);
      tex.generateMipmaps = false;
      this.defaultTexCache[gl] = tex;
    }
    return this.defaultTexCache[gl];
  },

  upload : function() {
    var gl = this.gl;
    var target = gl[this.target];
    if (this.image) {
      var img = this.image;
      if (this.image.tagName == 'VIDEO' &&
          (/WebKit\/\d+/).test(window.navigator.userAgent))
      {
        if (!this.image.tmpCanvas ||
            this.image.tmpCanvas.width != this.image.width ||
            this.image.tmpCanvas.height != this.image.height)
        {
          this.image.tmpCanvas = E.canvas(this.image.width, this.image.height);
        }
        this.image.tmpCanvas.getContext('2d')
            .drawImage(this.image, 0, 0, this.image.width, this.image.height);
        img = this.image.tmpCanvas;
      }
      this.width = img.width;
      this.height = img.height;
      if (this.previousWidth == this.width && this.previousHeight == this.height)
      {
        gl.texSubImage2D(target, 0, 0, 0, gl.RGBA, gl.UNSIGNED_BYTE, img);
      } else {
        gl.texImage2D(target, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
      }
    } else {
      if (this.previousWidth == this.width && this.previousHeight == this.height)
      {
        gl.texImage2D(target, 0, 0, 0, this.width, this.height,
                      gl.RGBA, gl.UNSIGNED_BYTE, this.data);
      } else {
        gl.texImage2D(target, 0, gl.RGBA, this.width, this.height, 0,
                      gl.RGBA, gl.UNSIGNED_BYTE, this.data);
      }
    }
    this.previousWidth = this.width;
    this.previousHeight = this.height;
    Magi.checkError(gl, "Texture.upload");
  },
  
  regenerateMipmap : function() {
    var gl = this.gl;
    var target = gl[this.target];
    gl.texParameteri(target, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    if (this.generateMipmaps) {
      gl.generateMipmap(target);
      gl.texParameteri(target, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR);
    }
  },
  
  compile: function(){
    var gl = this.gl;
    var target = gl[this.target];
    this.textureObject = gl.createTexture();
    Magi.Stats.textureCreationCount++;
    gl.bindTexture(target, this.textureObject);
    this.upload();
    gl.texParameteri(target, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(target, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texParameteri(target, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    this.regenerateMipmap();
  },
  
  use : function() {
    if (this.textureObject == null)
      this.compile();
    this.gl.bindTexture(this.gl[this.target], this.textureObject);
    if (this.changed) {
      this.upload();
      this.regenerateMipmap();
      this.changed = false;
    }
  },

  clear : function() {
    if (this.textureObject)
      this.gl.deleteTexture(this.textureObject);
    this.previousWidth = this.previousHeight = null;
    this.textureObject = null;
  },

  destroy : function() {
    this.clear();
    Magi.AllocatedResources.deleteTexture(this);
  }
});


Magi.Shader = Klass({
  id : null,
  gl : null,
  compiled : false,
  shader : null,
  shaders : [],

  initialize : function(gl){
    this.gl = gl;
    this.shaders = [];
    this.uniformLocations = {};
    this.attribLocations = {};
    for (var i=1; i<arguments.length; i++) {
      this.shaders.push(arguments[i]);
    }
    Magi.AllocatedResources.addShader(this);
  },

  destroy : function() {
    if (this.shader != null) 
      Magi.Shader.deleteShader(this.gl, this.shader);
    Magi.AllocatedResources.deleteShader(this);
  },

  compile : function() {
    this.shader = Magi.Shader.getProgramByMixedArray(this.gl, this.shaders);
  },

  use : function() {
    if (this.shader == null)
      this.compile();
    this.gl.useProgram(this.shader.program);
  },
  
  getInfoLog : function() {
    if (this.shader == null) 
      this.compile();
    var gl = this.gl;
    var plog = gl.getProgramInfoLog(this.shader.program);
    var slog = this.shader.shaders.map(function(s){ return gl.getShaderInfoLog(s); }).join("\n\n");
    return plog + "\n\n" + slog;
  },

  uniform1fv : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform1fv(loc, value);
  },

  uniform2fv : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform2fv(loc, value);
  },

  uniform3fv : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform3fv(loc, value);
  },

  uniform4fv : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform4fv(loc, value);
  },
  
  uniform1f : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform1f(loc, value);
  },

  uniform2f : function(name, v1,v2) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform2f(loc, v1,v2);
  },

  uniform3f : function(name, v1,v2,v3) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform3f(loc, v1,v2,v3);
  },

  uniform4f : function(name, v1,v2,v3,v4) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform4f(loc, v1, v2, v3, v4);
  },
  
  uniform1iv : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform1iv(loc, value);
  },

  uniform2iv : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform2iv(loc, value);
  },

  uniform3iv : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform3iv(loc, value);
  },

  uniform4iv : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform4iv(loc, value);
  },

  uniform1i : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform1i(loc, value);
  },

  uniform2i : function(name, v1,v2) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform2i(loc, v1,v2);
  },

  uniform3i : function(name, v1,v2,v3) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform3i(loc, v1,v2,v3);
  },

  uniform4i : function(name, v1,v2,v3,v4) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniform4i(loc, v1, v2, v3, v4);
  },

  uniformMatrix4fv : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniformMatrix4fv(loc, false, value);
  },

  uniformMatrix3fv : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniformMatrix3fv(loc, false, value);
  },

  uniformMatrix2fv : function(name, value) {
    var loc = this.uniform(name).index;
    if (loc != null) this.gl.uniformMatrix2fv(loc, false, value);
  },

  attrib : function(name) {
    if (this.attribLocations[name] == null) {
      var loc = this.gl.getAttribLocation(this.shader.program, name);
      this.attribLocations[name] = {index: loc, current: null};
    }
    return this.attribLocations[name];
  },

  uniform : function(name) {
    if (this.uniformLocations[name] == null) {
      var loc = this.gl.getUniformLocation(this.shader.program, name);
      this.uniformLocations[name] = {index: loc, current: null};
    }
    return this.uniformLocations[name];
  }
});

Magi.Shader.createShader = function(gl, type, source) {
  if (typeof type == 'string') type = gl[type];
  var shader = gl.createShader(type);

  gl.shaderSource(shader, source);
  gl.compileShader(shader);

  if (gl.getShaderParameter(shader, gl.COMPILE_STATUS) != 1) {
    var ilog = gl.getShaderInfoLog(shader);
    gl.deleteShader(shader);
    throw(new Error("Failed to compile shader. Shader info log: " + ilog));
  }
  return shader;
}

Magi.Shader.getShaderById = function(gl, id) {
  var el = document.getElementById(id);
  if (!el) throw(new Error("getShaderById: No element has id "+id));
  var type, stype = el.getAttribute("type");
  if (stype == "text/x-glsl-fs")
    type = gl.FRAGMENT_SHADER;
  else if (stype == "text/x-glsl-vs")
    type = gl.VERTEX_SHADER;
  else
    throw(new Error("getShaderById: Unknown shader type "+stype));
  return this.createShader(gl, type, el.textContent);
}

Magi.Shader.loadShader = function(gl, src, callback, onerror, type) {
  if (!type) {
    var a = src.split(".");
    var ext = a[a.length-1].toLowerCase();
    if (ext == 'frag') type = gl.FRAGMENT_SHADER;
    else if (ext == 'vert') type = gl.VERTEX_SHADER;
    else throw(new Error("loadShader: Unknown shader extension "+ext));
  }
  var self = this;
  var xhr = new XMLHttpRequest;
  xhr.onsuccess = function(res) {
    var shader = self.createShader(gl, type, res.responseText);
    callback(shader, res);
  };
  xhr.onerror = function(res) {
    if (onerror)
      onerror(res);
    else
      throw(new Error("loadShader: Failed to load shader "+res.status));
  };
  xhr.open("GET", src, true);
  xhr.send(null);
  return xhr;
}

Magi.Shader.createProgram = function(gl, shaders) {
  var id = gl.createProgram();
  var shaderObjs = [];
  for (var i=0; i<shaders.length; ++i) {
    try {
      var sh = shaders[i];
      shaderObjs.push(sh);
      gl.attachShader(id, sh);
    } catch (e) {
      var pr = {program: id, shaders: shaderObjs};
      this.deleteShader(gl, pr);
      throw (e);
    }
  }
  var prog = {program: id, shaders: shaderObjs};
  gl.linkProgram(id);
  gl.validateProgram(id);
  if (gl.getProgramParameter(id, gl.LINK_STATUS) != 1) {
    this.deleteShader(gl,prog);
    throw(new Error("Failed to link shader: "+gl.getProgramInfoLog(id)));
  }
  if (gl.getProgramParameter(id, gl.VALIDATE_STATUS) != 1) {
    this.deleteShader(gl,prog);
    throw(new Error("Failed to validate shader"));
  }
  return prog;
}
Magi.Shader.loadProgramArray = function(gl, sources, callback, onerror) {
  var self = this;
  var sourcesCopy = sources.slice(0);
  var shaders = [];
  var iterate;
  iterate = function(sh) {
    shaders.push(sh);
    if (sourcesCopy.length == 0) {
      try {
        var p = self.createProgram(gl, shaders);
        callback(p);
      } catch (e) { onerror(e); }
    } else {
      var src = sourcesCopy.shift();
      self.loadShader(gl, src, iterate, onerror);
    }
  }
  var src = sourcesCopy.shift();
  self.loadShader(gl, src, iterate, onerror);
}
Magi.Shader.loadProgram = function(gl, callback) {
  var sh = [];
  for (var i=1; i<arguments.length; ++i)
    sh.push(arguments[i]);
  return this.loadProgramArray(gl, sh, callback);
}
Magi.Shader.getProgramBySourceArray = function(gl,shaders) {
  var self = this;
  var arr = shaders.map(function(sh) { return self.createShader(gl, sh.type, sh.text); });
  return this.createProgram(gl, arr);
}
Magi.Shader.getProgramByIdArray = function(gl,shaders) {
  var self = this;
  var arr = shaders.map(function(sh) { return self.getShaderById(gl, sh); });
  return this.createProgram(gl, arr);
}
Magi.Shader.getProgramByMixedArray = function(gl,shaders) {
  var self = this;
  var arr = shaders.map(function(sh) {
    if (sh.type)
      return self.createShader(gl, sh.type, sh.text);
    else
      return self.getShaderById(gl, sh);
  });
  return this.createProgram(gl, arr);
}
Magi.Shader.getProgramByIds = function(gl) {
  var sh = [];
  for (var i=1; i<arguments.length; ++i)
    sh.push(arguments[i]);
  return this.getProgramByIdArray(gl, sh);
}

Magi.Shader.deleteShader = function(gl, sh) {
  gl.useProgram(null);
  sh.shaders.forEach(function(s){
    gl.detachShader(sh.program, s);
    gl.deleteShader(s);
  });
  gl.deleteProgram(sh.program);
}
Magi.Shader.load = function(gl, callback) {
  var sh = [];
  for (var i=1; i<arguments.length; ++i)
    sh.push(arguments[i]);
  var s = new Shader(gl);
  Magi.Shader.loadProgramArray(gl, sh, function(p) {
    s.shader = p;
    s.compile = function(){};
    callback(s);
  });
}

Magi.Filter = Klass(Magi.Shader, {
  initialize : function(gl, shader) {
    Magi.Shader.initialize.apply(this, arguments);
  },

  apply : function(init) {
    this.use();
    var va = this.attrib("Vertex");
    var ta = this.attrib("TexCoord");
    var vbo = Magi.Geometry.Quad.getCachedVBO(this.gl);
    if (init) init(this);
    vbo.draw(va, null, ta);
  }
});

Magi.VBO = Klass({
    initialized : false,
    length : 0,
    vbos : null,
    type : 'TRIANGLES',
    elementsVBO : null,
    elements : null,

    initialize : function(gl) {
      this.gl = gl;
      this.data = [];
      this.elementsVBO = null;
      for (var i=1; i<arguments.length; i++) {
        if (arguments[i].elements)
          this.elements = arguments[i];
        else
          this.data.push(arguments[i]);
      }
      Magi.AllocatedResources.addVBO(this);
    },

  setData : function() {
    this.clear();
    this.data = [];
    for (var i=0; i<arguments.length; i++) {
      if (arguments[i].elements)
        this.elements = arguments[i];
      else
        this.data.push(arguments[i]);
    }
  },

  clear : function() {
    if (this.vbos != null)
      for (var i=0; i<this.vbos.length; i++)
        this.gl.deleteBuffer(this.vbos[i]);
    if (this.elementsVBO != null)
      this.gl.deleteBuffer(this.elementsVBO);
    this.length = this.elementsLength = 0;
    this.vbos = this.elementsVBO = null;
    this.initialized = false;
  },

  destroy : function() {
    this.clear();
    Magi.AllocatedResources.deleteVBO(this);
  },

  init : function() {
    this.clear();
    var gl = this.gl;
   
    gl.getError();
    var vbos = [];
    var length = 0;
    for (var i=0; i<this.data.length; i++)
      vbos.push(gl.createBuffer());
    if (this.elements != null)
      this.elementsVBO = gl.createBuffer();
    try {
      Magi.throwError(gl, "genBuffers");
      for (var i = 0; i<this.data.length; i++) {
        var d = this.data[i];
        var dlen = Math.floor(d.data.length / d.size);
        if (i == 0 || dlen < length)
            length = dlen;
        if (!d.floatArray)
          d.floatArray = new Float32Array(d.data);
        gl.bindBuffer(gl.ARRAY_BUFFER, vbos[i]);
        Magi.Stats.bindBufferCount++;
        Magi.throwError(gl, "bindBuffer");
        gl.bufferData(gl.ARRAY_BUFFER, d.floatArray, gl.STATIC_DRAW);
        Magi.throwError(gl, "bufferData");
      }
      if (this.elementsVBO != null) {
        var d = this.elements;
        this.elementsLength = d.data.length;
        this.elementsType = d.type == gl.UNSIGNED_BYTE ? d.type : gl.UNSIGNED_SHORT;
        gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this.elementsVBO);
        Magi.Stats.bindBufferCount++;
        Magi.throwError(gl, "bindBuffer ELEMENT_ARRAY_BUFFER");
        if (this.elementsType == gl.UNSIGNED_SHORT && !d.ushortArray) {
          d.ushortArray = new Uint16Array(d.data);
          gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, d.ushortArray, gl.STATIC_DRAW);
        } else if (this.elementsType == gl.UNSIGNED_BYTE && !d.ubyteArray) {
          d.ubyteArray = new Uint8Array(d.data);
          gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, d.ubyteArray, gl.STATIC_DRAW);
        }
        Magi.throwError(gl, "bufferData ELEMENT_ARRAY_BUFFER");
      }
    } catch(e) {
      for (var i=0; i<vbos.length; i++)
        gl.deleteBuffer(vbos[i]);
      throw(e);
    }

    gl.bindBuffer(gl.ARRAY_BUFFER, null);
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, null);
    Magi.Stats.bindBufferCount++;
    Magi.Stats.bindBufferCount++;

    this.length = length;
    this.vbos = vbos;
  
    this.initialized = true;
  },

  use : function() {
    if (!this.initialized) this.init();
    var gl = this.gl;
    for (var i=0; i<arguments.length; i++) {
      var arg = arguments[i];
      var vbo = this.vbos[i];
      if (arg == null || arg.index == null || arg.index == -1) continue;
      if (!vbo) {
        gl.disableVertexAttribArray(arg.index);
        continue;
      }
      if (Magi.VBO[arg.index] !== vbo) {
        gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
        gl.vertexAttribPointer(arg.index, this.data[i].size, gl.FLOAT, false, 0, 0);
        Magi.Stats.bindBufferCount++;
        Magi.Stats.vertexAttribPointerCount++;
      }
      gl.enableVertexAttribArray(arg.index);
      Magi.VBO[arg.index] = vbo;
    }
    if (this.elementsVBO != null) {
      gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this.elementsVBO);
      Magi.Stats.bindBufferCount++;
    }
  },

  draw : function() {
    var args = [];
    this.use.apply(this, arguments);
    var gl = this.gl;
    if (this.elementsVBO != null) {
      gl.drawElements(gl[this.type], this.elementsLength, this.elementsType, 0);
      Magi.Stats.drawElementsCount++;
    } else {
      gl.drawArrays(gl[this.type], 0, this.length);
      Magi.Stats.drawArraysCount++;
    }
  }
});

Magi.FBO = Klass({
  initialized : false,
  useDepth : true,
  fbo : null,
  rbo : null,
  texture : null,

  initialize : function(gl, width, height, use_depth) {
    this.gl = gl;
    this.width = width;
    this.height = height;
    if (use_depth != null)
      this.useDepth = use_depth;
    Magi.AllocatedResources.addFBO(this);
  },

  destroy : function() {
    if (this.fbo) this.gl.deleteFramebuffer(this.fbo);
    if (this.rbo) this.gl.deleteRenderbuffer(this.rbo);
    if (this.texture) this.gl.deleteTexture(this.texture);
    Magi.AllocatedResources.deleteFBO(this);
  },

  init : function() {
    var gl = this.gl;
    var w = this.width, h = this.height;
    var fbo = this.fbo != null ? this.fbo : gl.createFramebuffer();
    var rb;

    gl.bindFramebuffer(gl.FRAMEBUFFER, fbo);
    Magi.checkError(gl, "FBO.init bindFramebuffer");
    if (this.useDepth) {
      rb = this.rbo != null ? this.rbo : gl.createRenderbuffer();
      gl.bindRenderbuffer(gl.RENDERBUFFER, rb);
      Magi.checkError(gl, "FBO.init bindRenderbuffer");
      gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT16, w, h);
      Magi.checkError(gl, "FBO.init renderbufferStorage");
    }

    var tex = this.texture != null ? this.texture : gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, tex);
    try {
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
    } catch (e) { // argh, no null texture support
      var tmp = this.getTempCanvas(w,h);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, tmp);
    }
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    Magi.checkError(gl, "FBO.init tex");

    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, tex, 0);
    Magi.checkError(gl, "FBO.init bind tex");

    if (this.useDepth) {
      gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, rb);
      Magi.checkError(gl, "FBO.init bind depth buffer");
    }

    var fbstat = gl.checkFramebufferStatus(gl.FRAMEBUFFER);
    if (fbstat != gl.FRAMEBUFFER_COMPLETE) {
      var glv;
      for (var v in gl) {
        try { glv = gl[v]; } catch (e) { glv = null; }
        if (glv == fbstat) { fbstat = v; break; }}
    }
    Magi.checkError(gl, "FBO.init check fbo");

    this.fbo = fbo;
    this.rbo = rb;
    this.texture = tex;
    this.initialized = true;
  },

  getTempCanvas : function(w, h) {
    if (!Magi.FBO.tempCanvas) {
      Magi.FBO.tempCanvas = document.createElement('canvas');
    }
    Magi.FBO.tempCanvas.width = w;
    Magi.FBO.tempCanvas.height = h;
    return Magi.FBO.tempCanvas;
  },

  use : function() {
    if (!this.initialized) this.init();
    this.gl.bindFramebuffer(this.gl.FRAMEBUFFER, this.fbo);
  }
});

Magi.makeGLErrorWrapper = function(gl, fname) {
    return (function() {
        var rv;
        try {
            rv = gl[fname].apply(gl, arguments);
        } catch (e) {
            throw(new Error("GL error " + e.name + " in "+fname+ "\n"+ e.message+"\n" +arguments.callee.caller));
        }
        var e = gl.getError();
        if (e != 0) {
            throw(new Error("GL error "+e+" in "+fname));
        }
        return rv;
    });
}

Magi.wrapGLContext = function(gl) {
    var wrap = {};
    for (var i in gl) {
      try {
        if (typeof gl[i] == 'function') {
            wrap[i] = Magi.makeGLErrorWrapper(gl, i);
        } else {
            wrap[i] = gl[i];
        }
      } catch (e) {
        // log("wrapGLContext: Error accessing " + i);
      }
    }
    wrap.getError = function(){ return gl.getError(); };
    return wrap;
}

Magi.Geometry = {};

Magi.Geometry.Quad = {
  vertices : new Float32Array([
    -1,-1,0,
    1,-1,0,
    -1,1,0,
    1,-1,0,
    1,1,0,
    -1,1,0
  ]),
  normals : new Float32Array([
    0,0,-1,
    0,0,-1,
    0,0,-1,
    0,0,-1,
    0,0,-1,
    0,0,-1
  ]),
  texcoords : new Float32Array([
    0,0,
    1,0,
    0,1,
    1,0,
    1,1,
    0,1
  ]),
  indices : new Float32Array([0,1,2,1,5,2]),
  makeVBO : function(gl) {
    return new Magi.VBO(gl,
        {size:3, data: this.vertices},
        {size:3, data: this.normals},
        {size:2, data: this.texcoords}
    )
  },
  cache: {},
  getCachedVBO : function(gl) {
    if (!this.cache[gl])
      this.cache[gl] = this.makeVBO(gl);
    return this.cache[gl];
  }
};

Magi.Geometry.QuadMesh = {
  makeVBO : function(gl, xCount, yCount) {
    var vertices = [], normals = [], texcoords = [];
    for (var x=0; x<xCount; x++) {
      for (var y=0; y<yCount; y++) {
        vertices.push((x-(xCount/2)) / (xCount/2), (y-(yCount/2)) / (yCount/2), 0);
        vertices.push(((x+1)-(xCount/2)) / (xCount/2), (y-(yCount/2)) / (yCount/2), 0);
        vertices.push((x-(xCount/2)) / (xCount/2), ((y+1)-(yCount/2)) / (yCount/2), 0);
        vertices.push(((x+1)-(xCount/2)) / (xCount/2), (y-(yCount/2)) / (yCount/2), 0);
        vertices.push(((x+1)-(xCount/2)) / (xCount/2), ((y+1)-(yCount/2)) / (yCount/2), 0);
        vertices.push((x-(xCount/2)) / (xCount/2), ((y+1)-(yCount/2)) / (yCount/2), 0);
        normals.push(0,0,-1);
        normals.push(0,0,-1);
        normals.push(0,0,-1);
        normals.push(0,0,-1);
        normals.push(0,0,-1);
        normals.push(0,0,-1);
        texcoords.push(x/xCount, y/yCount);
        texcoords.push((x+1)/xCount, y/yCount);
        texcoords.push(x/xCount, (y+1)/yCount);
        texcoords.push((x+1)/xCount, y/yCount);
        texcoords.push((x+1)/xCount, (y+1)/yCount);
        texcoords.push(x/xCount, (y+1)/yCount);
      }
    }
    return new Magi.VBO(gl,
        {size:3, data: new Float32Array(vertices)},
        {size:3, data: new Float32Array(normals)},
        {size:2, data: new Float32Array(texcoords)}
    )
  },
  cache: {},
  getCachedVBO : function(gl, xCount, yCount) {
    xCount = xCount || 50;
    yCount = yCount || 50;
    var k = xCount +":"+ yCount;
    if (!this.cache[gl]) {
      this.cache[gl] = {};
    }
    if (!this.cache[gl][k]) {
      this.cache[gl][k] = this.makeVBO(gl, xCount, yCount);
    }
    return this.cache[gl][k];
  }
};

Magi.Geometry.Cube = {
  vertices : new Float32Array([  0.5, -0.5,  0.5, // +X
                0.5, -0.5, -0.5,
                0.5,  0.5, -0.5,
                0.5,  0.5,  0.5,

                0.5,  0.5,  0.5, // +Y
                0.5,  0.5, -0.5,
                -0.5,  0.5, -0.5,
                -0.5,  0.5,  0.5,

                0.5,  0.5,  0.5, // +Z
                -0.5,  0.5,  0.5,
                -0.5, -0.5,  0.5,
                0.5, -0.5,  0.5,

                -0.5, -0.5,  0.5, // -X
                -0.5,  0.5,  0.5,
                -0.5,  0.5, -0.5,
                -0.5, -0.5, -0.5,

                -0.5, -0.5,  0.5, // -Y
                -0.5, -0.5, -0.5,
                0.5, -0.5, -0.5,
                0.5, -0.5,  0.5,

                -0.5, -0.5, -0.5, // -Z
                -0.5,  0.5, -0.5,
                0.5,  0.5, -0.5,
                0.5, -0.5, -0.5,
      ]),

  normals : new Float32Array([ 1, 0, 0,
              1, 0, 0,
              1, 0, 0,
              1, 0, 0,

              0, 1, 0,
              0, 1, 0,
              0, 1, 0,
              0, 1, 0,

              0, 0, 1,
              0, 0, 1,
              0, 0, 1,
              0, 0, 1,

              -1, 0, 0,
              -1, 0, 0,
              -1, 0, 0,
              -1, 0, 0,

              0,-1, 0,
              0,-1, 0,
              0,-1, 0,
              0,-1, 0,

              0, 0,-1,
              0, 0,-1,
              0, 0,-1,
              0, 0,-1
      ]),

  texcoords :  new Float32Array([
    0,0,  0,1,  1,1, 1,0,
    0,0,  0,1,  1,1, 1,0,
    0,0,  0,1,  1,1, 1,0,
    0,0,  0,1,  1,1, 1,0,
    0,0,  0,1,  1,1, 1,0,
    0,0,  0,1,  1,1, 1,0
  ]),
      
  indices : [],
  create : function(){
    for (var i = 0; i < 6; i++) {
      this.indices.push(i*4 + 0);
      this.indices.push(i*4 + 1);
      this.indices.push(i*4 + 3);
      this.indices.push(i*4 + 1);
      this.indices.push(i*4 + 2);
      this.indices.push(i*4 + 3);
    }
    this.indices = new Float32Array(this.indices);
  },

  makeVBO : function(gl) {
    return new Magi.VBO(gl,
        {size:3, data: this.vertices},
        {size:3, data: this.normals},
        {size:2, data: this.texcoords},
        {elements: true, data: this.indices}
    );
  },
  cache : {},
  getCachedVBO : function(gl) {
    if (!this.cache[gl])
      this.cache[gl] = this.makeVBO(gl);
    return this.cache[gl];
  }
};
Magi.Geometry.Cube.create();

Magi.Geometry.Sphere = {
  vertices : [],
  normals : [],
  indices : [],
  create : function(){
    var r = 0.75;
    var self = this;
    function vert(theta, phi)
    {
      var r = 0.75;
      var x, y, z, nx, ny, nz;

      nx = Math.sin(theta) * Math.cos(phi);
      ny = Math.sin(phi);
      nz = Math.cos(theta) * Math.cos(phi);
      self.normals.push(nx);
      self.normals.push(ny);
      self.normals.push(nz);

      x = r * Math.sin(theta) * Math.cos(phi);
      y = r * Math.sin(phi);
      z = r * Math.cos(theta) * Math.cos(phi);
      self.vertices.push(x);
      self.vertices.push(y);
      self.vertices.push(z);
    }
    for (var phi = -Math.PI/2; phi < Math.PI/2; phi += Math.PI/20) {
      var phi2 = phi + Math.PI/20;
      for (var theta = -Math.PI/2; theta <= Math.PI/2; theta += Math.PI/20) {
        vert(theta, phi);
        vert(theta, phi2);
      }
    }
  }
};
Magi.Geometry.Sphere.create();


Magi.Geometry.Ring = {
  makeXZQuad : function(x,y,z,x2,y2,z2,vertices) {
    vertices.push(x, y, z);
    vertices.push(x2, y, z2);
    vertices.push(x, y2, z);
    vertices.push(x2, y, z2);
    vertices.push(x2, y2, z2);
    vertices.push(x, y2, z);
  },
  makeVBO : function(gl, height, segments, yCount, angle) {
    var vertices = [], normals = [], texcoords = [];
    for (var s=0; s<segments; s++) {
      var ra1 = s/segments;
      var ra2 = (s+1)/segments;
      var a1 = ra1 * angle;
      var a2 = ra2 * angle;
      var x1 = Math.cos(a1);
      var x2 = Math.cos(a2);
      var z1 = Math.sin(a1);
      var z2 = Math.sin(a2);
      for (var y=0; y<yCount; y++) {
        var y1 = 2 * height * (-0.5 + y/yCount);
        var y2 = 2 * height * (-0.5 + (y+1)/yCount);
        this.makeXZQuad(x1,y1,z1,x2,y2,z2,vertices);
        normals.push(z1, 0, -x1);
        normals.push(z2, 0, -x2);
        normals.push(z1, 0, -x1);
        normals.push(z2, 0, -x2);
        normals.push(z2, 0, -x2);
        normals.push(z1, 0, -x1);
        texcoords.push(ra1, y1);
        texcoords.push(ra2, y1);
        texcoords.push(ra1, y2);
        texcoords.push(ra2, y1);
        texcoords.push(ra2, y2);
        texcoords.push(ra1, y2);
      }
    }
    return new Magi.VBO(gl,
        {size:3, data: new Float32Array(vertices)},
        {size:3, data: new Float32Array(normals)},
        {size:2, data: new Float32Array(texcoords)}
    )
  },
  cache: {},
  getCachedVBO : function(gl, height, segments, yCount, angle) {
    height = height || 0.1;
    segments = segments || 256;
    yCount = yCount || 10;
    angle = angle || Math.PI*2;
    var k = segments +":"+ yCount + ":" + angle + ":" + height;
    if (!this.cache[gl]) {
      this.cache[gl] = {};
    }
    if (!this.cache[gl][k]) {
      this.cache[gl][k] = this.makeVBO(gl, height, segments, yCount, angle);
    }
    return this.cache[gl][k];
  }
}


Magi.log=function(msg) {
  if (window.console)
    console.log(msg);
  if (this.logCanvas) {
    var c = this.logCanvas;
    var ctx = c.getContext('2d');
    ctx.font = '14px Sans-serif';
    ctx.textAlign = 'center';
    ctx.fillStyle = '#c24';
    ctx.fillText(msg,c.width/2,c.height/2,c.width-20);
  }
  if (this.logElement) {
    this.logElement.appendChild(P(T(msg)));
  }
}
Magi.GL_CONTEXT_ID = null;
Magi.getGLContext = function(c, args){
  var find=function(a,f){for(var i=0,j;j=a[i],i++<a.length;)if(f(j))return j};
  if (!this.GL_CONTEXT_ID)
    this.GL_CONTEXT_ID = find(['webgl','experimental-webgl'],function(n){try{return c.getContext(n)}catch(e){}});
  if (!this.GL_CONTEXT_ID) {
    this.logCanvas = c;
    this.log("No WebGL context found. Click here for more details.");
    var a = document.createElement('a');
    a.href = "http://khronos.org/webgl/wiki/Getting_a_WebGL_Implementation";
    c.parentNode.insertBefore(a, c);
    a.appendChild(c);
  }
  else return c.getContext(this.GL_CONTEXT_ID, args); 
}

Magi.Stats = {
  shaderBindCount : 0,
  materialUpdateCount : 0,
  uniformSetCount : 0,
  textureSetCount : 0,
  textureCreationCount : 0,
  vertexAttribPointerCount : 0,
  bindBufferCount : 0,
  drawElementsCount : 0,
  drawArraysCount : 0,
  reset : function(){
    this.shaderBindCount = 0;
    this.materialUpdateCount = 0;
    this.uniformSetCount = 0;
    this.textureSetCount = 0;
    this.textureCreationCount = 0;
    this.vertexAttribPointerCount = 0;
    this.bindBufferCount = 0;
    this.drawElementsCount = 0;
    this.drawArraysCount = 0;
  },
  print : function(elem) {
    elem.textContent =
      'Shader bind count: ' + this.shaderBindCount + '\n' +
      'Material update count: ' + this.materialUpdateCount + '\n' +
      'Uniform set count: ' + this.uniformSetCount + '\n' +
      'Texture creation count: ' + this.textureCreationCount + '\n' +
      'Texture set count: ' + this.textureSetCount + '\n' +
      'VertexAttribPointer count: ' + this.vertexAttribPointerCount + '\n' +
      'Bind buffer count: ' + this.bindBufferCount + '\n' +
      'Draw elements count: ' + this.drawElementsCount + '\n' +
      'Draw arrays count: ' + this.drawArraysCount + '\n' +
      '';
  }
}

Magi.Curves = {

  angularDistance : function(a, b) {
    var pi2 = Math.PI*2;
    var d = (b - a) % pi2;
    if (d > Math.PI) d -= pi2;
    if (d < -Math.PI) d += pi2;
    return d;
  },

  linePoint : function(a, b, t, dest) {
    if (!dest) dest = vec3.create();
    dest[0] = a[0]+(b[0]-a[0])*t
    dest[1] = a[1]+(b[1]-a[1])*t;
    dest[2] = a[2]+(b[2]-a[2])*t;
    return dest;
  },

  quadraticPoint : function(a, b, c, t, dest) {
    if (!dest) dest = vec3.create();
    // var d = this.linePoint(a,b,t)
    // var e = this.linePoint(b,c,t)
    // return this.linePoint(d,e,t)
    var dx = a[0]+(b[0]-a[0])*t;
    var ex = b[0]+(c[0]-b[0])*t;
    var x = dx+(ex-dx)*t;
    var dy = a[1]+(b[1]-a[1])*t;
    var ey = b[1]+(c[1]-b[1])*t;
    var y = dy+(ey-dy)*t;
    var dz = a[2]+(b[2]-a[2])*t;
    var ez = b[2]+(c[2]-b[2])*t;
    var z = dz+(ez-dz)*t;
    dest[0] = x; dest[1] = y; dest[2] = z;
    return dest;
  },

  cubicPoint : function(a, b, c, d, t, dest) {
    if (!dest) dest = vec3.create();
    var ax3 = a[0]*3;
    var bx3 = b[0]*3;
    var cx3 = c[0]*3;
    var ay3 = a[1]*3;
    var by3 = b[1]*3;
    var cy3 = c[1]*3;
    var az3 = a[2]*3;
    var bz3 = b[2]*3;
    var cz3 = c[2]*3;
    var x = a[0] + t*(bx3 - ax3 + t*(ax3-2*bx3+cx3 + t*(bx3-a[0]-cx3+d[0])));
    var y = a[1] + t*(by3 - ay3 + t*(ay3-2*by3+cy3 + t*(by3-a[1]-cy3+d[1])));
    var z = a[2] + t*(bz3 - az3 + t*(az3-2*bz3+cz3 + t*(bz3-a[2]-cz3+d[2])));
    dest[0] = x; dest[1] = y; dest[2] = z;
    return dest;
  },

  linearValue : function(a,b,t) {
    return a + (b-a)*t;
  },

  quadraticValue : function(a,b,c,t) {
    var d = a + (b-a)*t;
    var e = b + (c-b)*t;
    return d + (e-d)*t;
  },

  cubicValue : function(a,b,c,d,t) {
    var a3 = a*3, b3 = b*3, c3 = c*3;
    return a + t*(b3 - a3 + t*(a3-2*b3+c3 + t*(b3-a-c3+d)));
  },

  catmullRomPoint : function (a,b,c,d, t, dest) {
    if (!dest) dest = vec3.create();
    var af = ((-t+2)*t-1)*t*0.5;
    var bf = (((3*t-5)*t)*t+2)*0.5;
    var cf = ((-3*t+4)*t+1)*t*0.5;
    var df = ((t-1)*t*t)*0.5;
    var x = a[0]*af + b[0]*bf + c[0]*cf + d[0]*df;
    var y = a[1]*af + b[1]*bf + c[1]*cf + d[1]*df;
    var z = a[2]*af + b[2]*bf + c[2]*cf + d[2]*df;
    dest[0] = x; dest[1] = y; dest[2] = z;
    return dest;
  },

/*
  catmullRomAngle : function (a,b,c,d, t) {
    var dx = 0.5 * (c[0] - a[0] + 2*t*(2*a[0] - 5*b[0] + 4*c[0] - d[0]) +
             3*t*t*(3*b[0] + d[0] - a[0] - 3*c[0]))
    var dy = 0.5 * (c[1] - a[1] + 2*t*(2*a[1] - 5*b[1] + 4*c[1] - d[1]) +
             3*t*t*(3*b[1] + d[1] - a[1] - 3*c[1]))
    return Math.atan2(dy, dx)
  },

  catmullRomPointAngle : function (a,b,c,d, t) {
    var p = this.catmullRomPoint(a,b,c,d,t)
    var a = this.catmullRomAngle(a,b,c,d,t)
    return {point:p, angle:a}
  },

  lineAngle : function(a,b) {
    return Math.atan2(b[1]-a[1], b[0]-a[0])
  },

  quadraticAngle : function(a,b,c,t) {
    var d = this.linePoint(a,b,t)
    var e = this.linePoint(b,c,t)
    return this.lineAngle(d,e)
  },

  cubicAngle : function(a, b, c, d, t) {
    var e = this.quadraticPoint(a,b,c,t)
    var f = this.quadraticPoint(b,c,d,t)
    return this.lineAngle(e,f)
  },
*/

  lineLength : function(a,b) {
    var x = (b[0]-a[0]);
    var y = (b[1]-a[1]);
    var z = (b[2]-a[2]);
    return Math.sqrt(x*x + y*y + z*z);
  },

  squareLineLength : function(a,b) {
    var x = (b[0]-a[0]);
    var y = (b[1]-a[1]);
    var z = (b[2]-a[2]);
    return x*x + y*y + z*z;
  },

  quadraticLength : function(a,b,c, error) {
    var p1 = this.linePoint(a,b,2/3)
    var p2 = this.linePoint(b,c,1/3)
    return this.cubicLength(a,p1,p2,c, error)
  },

  cubicLength : (function() {
    var bezsplit = function(v) {
      var vtemp = [v.slice(0)];

      for (var i=1; i < 4; i++) {
        vtemp[i] = [[],[],[],[]];
        for (var j=0; j < 4-i; j++) {
          vtemp[i][j][0] = 0.5 * (vtemp[i-1][j][0] + vtemp[i-1][j+1][0]);
          vtemp[i][j][1] = 0.5 * (vtemp[i-1][j][1] + vtemp[i-1][j+1][1]);
        }
      }
      var left = [];
      var right = [];
      for (var j=0; j<4; j++) {
        left[j] = vtemp[j][0];
        right[j] = vtemp[3-j][j];
      }
      return [left, right];
    };

    var addifclose = function(v, error) {
      var len = 0;
      for (var i=0; i < 3; i++) {
        len += Curves.lineLength(v[i], v[i+1]);
      }
      var chord = Curves.lineLength(v[0], v[3]);
      if ((len - chord) > error) {
        var lr = bezsplit(v);
        len = addifclose(lr[0], error) + addifclose(lr[1], error);
      }
      return len;
    };

    return function(a,b,c,d, error) {
      if (!error) error = 1;
      return addifclose([a,b,c,d], error);
    };
  })()

/*
  quadraticLengthPointAngle : function(a,b,c,lt,error) {
    var p1 = this.linePoint(a,b,2/3);
    var p2 = this.linePoint(b,c,1/3);
    return this.cubicLengthPointAngle(a,p1,p2,c, error);
  },

  cubicLengthPointAngle : function(a,b,c,d,lt,error) {
    // how about not creating a billion arrays, hmm?
    var len = this.cubicLength(a,b,c,d,error)
    var point = a
    var prevpoint = a
    var lengths = []
    var prevlensum = 0
    var lensum = 0
    var tl = lt*len
    var segs = 20
    var fac = 1/segs
    for (var i=1; i<=segs; i++) { // FIXME get smarter
      prevpoint = point
      point = this.cubicPoint(a,b,c,d, fac*i)
      prevlensum = lensum
      lensum += this.lineLength(prevpoint, point)
      if (lensum >= tl) {
        if (lensum == prevlensum)
          return {point: point, angle: this.lineAngle(a,b)}
        var dl = lensum - tl
        var dt = dl / (lensum-prevlensum)
        return {point: this.linePoint(prevpoint, point, 1-dt),
                angle: this.cubicAngle(a,b,c,d, fac*(i-dt)) }
      }
    }
    return {point: d.slice(0), angle: this.lineAngle(c,d)}
  }
*/
}



/**
  Color helper functions.
  */
Magi.Colors = {

  /**
    Converts an HSL color to its corresponding RGB color.

    @param h Hue in degrees (0 .. 359)
    @param s Saturation (0.0 .. 1.0)
    @param l Lightness (0 .. 255)
    @return The corresponding RGB color as [r,g,b]
    @type Array
    */
  hsl2rgb : function(h,s,l) {
    var r,g,b;
    if (s == 0) {
      r=g=b=v;
    } else {
      var q = (l < 0.5 ? l * (1+s) : l+s-(l*s));
      var p = 2 * l - q;
      var hk = (h % 360) / 360;
      var tr = hk + 1/3;
      var tg = hk;
      var tb = hk - 1/3;
      if (tr < 0) tr++;
      if (tr > 1) tr--;
      if (tg < 0) tg++;
      if (tg > 1) tg--;
      if (tb < 0) tb++;
      if (tb > 1) tb--;
      if (tr < 1/6)
        r = p + ((q-p)*6*tr);
      else if (tr < 1/2)
        r = q;
      else if (tr < 2/3)
        r = p + ((q-p)*6*(2/3 - tr));
      else
        r = p;

      if (tg < 1/6)
        g = p + ((q-p)*6*tg);
      else if (tg < 1/2)
        g = q;
      else if (tg < 2/3)
        g = p + ((q-p)*6*(2/3 - tg));
      else
        g = p;

      if (tb < 1/6)
        b = p + ((q-p)*6*tb);
      else if (tb < 1/2)
        b = q;
      else if (tb < 2/3)
        b = p + ((q-p)*6*(2/3 - tb));
      else
        b = p;
    }

    return [r,g,b];
  },

  /**
    Converts an HSV color to its corresponding RGB color.

    @param h Hue in degrees (0 .. 359)
    @param s Saturation (0.0 .. 1.0)
    @param v Value (0 .. 255)
    @return The corresponding RGB color as [r,g,b]
    @type Array
    */
  hsv2rgb : function(h,s,v) {
    var r,g,b;
    if (s == 0) {
      r=g=b=v;
    } else {
      h = (h % 360)/60.0;
      var i = Math.floor(h);
      var f = h-i;
      var p = v * (1-s);
      var q = v * (1-s*f);
      var t = v * (1-s*(1-f));
      switch (i) {
        case 0:
          r = v;
          g = t;
          b = p;
          break;
        case 1:
          r = q;
          g = v;
          b = p;
          break;
        case 2:
          r = p;
          g = v;
          b = t;
          break;
        case 3:
          r = p;
          g = q;
          b = v;
          break;
        case 4:
          r = t;
          g = p;
          b = v;
          break;
        case 5:
          r = v;
          g = p;
          b = q;
          break;
      }
    }
    return [r,g,b];
  },

  /**
    Parses a color style object into one that can be used with the given
    canvas context.

    Accepted formats:
      'white'
      '#fff'
      '#ffffff'
      'rgba(255,255,255, 1.0)'
      [255, 255, 255]
      [255, 255, 255, 1.0]
      new Gradient(...)
      new Pattern(...)

    @param style The color style to parse
    @param ctx Canvas 2D context on which the style is to be used
    @return A parsed style, ready to be used as ctx.fillStyle / strokeStyle
    */
  parseColorStyle : function(style, ctx) {
    if (typeof style == 'string') {
      return style;
    } else if (style.compiled) {
      return style.compiled;
    } else if (style.isPattern) {
      return style.compile(ctx);
    } else if (style.length == 3) {
      return 'rgba('+style.map(Math.round).join(",")+', 1)';
    } else if (style.length == 4) {
      return 'rgba('+
              Math.round(style[0])+','+
              Math.round(style[1])+','+
              Math.round(style[2])+','+
              style[3]+
             ')';
    } else {
      throw( "Bad style: " + style );
    }
  }
}


R = function(start, end) {
  var a = []
  for (var i=start; i<end; i++) a.push(i)
  return a
}
Rg = function(start, last) {
  return R(start, last+1)
}

/**
  Delete the first instance of obj from the array.

  @param obj The object to delete
  @return true on success, false if array contains no instances of obj
  @type boolean
  @addon
  */
Array.prototype.deleteFirst = function(obj) {
  for (var i=0; i<this.length; i++) {
    if (this[i] == obj) {
      this.splice(i,1)
      return true
    }
  }
  return false
}

Array.prototype.stableSort = function(f) {
  for (var i=0; i<this.length; i++) {
    this[i].__stableSortIndex = i;
  }
  this.sort(function(a,b) {
    var v = f(a,b);
    if (v == 0)
      v = a.__stableSortIndex - b.__stableSortIndex;
    return v;
  });
  for (var i=0; i<this.length; i++) {
    delete this[i].__stableSortIndex;
  }
}

/**
  Returns true if f returns true for all elements in this.
  */
Array.prototype.all = function(f) {
  for (var i=0; i<this.length; i++) {
    if (!f(this[i], i, this)) return false
  }
  return true
}

/**
  Returns true if f returns true for any element in this.
  */
Array.prototype.any = function(f) {
  for (var i=0; i<this.length; i++) {
    if (f(this[i], i, this)) return true
  }
  return false
}

/**
  Returns true if all the elements in this are non-null attributes of obj.
  */
Array.prototype.allIn = function(obj) {
  return this.all(function(k){ return obj[k] != null })
}

/**
  Returns true if any element in this is a non-null attribute of obj.
  */
Array.prototype.anyIn = function(obj) {
  return this.any(function(k){ return obj[k] != null })
}

/**
  Compares two arrays for equality. Returns true if the arrays are equal.
  */
Array.prototype.equals = function(array) {
  if (!array) return false
  if (this.length != array.length) return false
  for (var i=0; i<this.length; i++) {
    var a = this[i]
    var b = array[i]
    if (a.equals && typeof(a.equals) == 'function') {
      if (!a.equals(b)) return false
    } else if (a != b) {
      return false
    }
  }
  return true
}

/**
  Rotates the first element of an array to be the last element.
  Rotates last element to be the first element when backToFront is true.

  @param {boolean} backToFront Whether to move the last element to the front or not
  @return The last element when backToFront is false, the first element when backToFront is true
  @addon
  */
Array.prototype.rotate = function(backToFront) {
  if (backToFront) {
    this.unshift(this.pop())
    return this[0]
  } else {
    this.push(this.shift())
    return this[this.length-1]
  }
}
/**
  Returns a random element from the array.

  @return A random element
  @addon
 */
Array.prototype.random = function() {
  return this[Math.floor(Math.random()*this.length)]
}

Array.prototype.flatten = function() {
  var a = []
  for (var i=0; i<this.length; i++) {
    var e = this[i]
    if (e.flatten) {
      var ef = e.flatten()
      for (var j=0; j<ef.length; j++) {
        a[a.length] = ef[j]
      }
    } else {
      a[a.length] = e
    }
  }
  return a
}

Array.prototype.take = function() {
  var a = []
  for (var i=0; i<this.length; i++) {
    var e = []
    for (var j=0; j<arguments.length; j++) {
      e[j] = this[i][arguments[j]]
    }
    a[i] = e
  }
  return a
}

if (!Array.prototype.pluck) {
  Array.prototype.pluck = function(key) {
    var a = []
    for (var i=0; i<this.length; i++) {
      a[i] = this[i][key]
    }
    return a
  }
}

Array.prototype.set = function(key, value) {
  for (var i=0; i<this.length; i++) {
    this[i][key] = value
  }
}

Array.prototype.allWith = function() {
  var a = []
  topLoop:
  for (var i=0; i<this.length; i++) {
    var e = this[i]
    for (var j=0; j<arguments.length; j++) {
      if (!this[i][arguments[j]])
        continue topLoop
    }
    a[a.length] = e
  }
  return a
}

Array.prototype.bsearch = function(key) {
  var low = 0
  var high = this.length - 1
  while (low <= high) {
    var mid = low + ((high - low) >> 1) // low + (high - low) / 2, int division
    var midVal = this[mid]

    if (midVal < key)
      low = mid + 1
    else if (midVal > key)
      high = mid - 1
    else
      return mid
  }
  return -1
}

Array.prototype.sortNum = function() {
  return this.sort(function(a,b){ return (a > b ? 1 : (a < b ? -1 : 0)) })
}

Element.prototype.append = function() {
  for(var i=0; i<arguments.length; i++) {
    if (typeof(arguments[i]) == 'string') {
      this.appendChild(T(arguments[i]))
    } else {
      this.appendChild(arguments[i])
    }
  }
}

// some common helper methods

if (!Function.prototype.bind) {
  /**
    Creates a function that calls this function in the scope of the given
    object.

      var obj = { x: 'obj' }
      var f = function() { return this.x }
      window.x = 'window'
      f()
      // => 'window'
      var g = f.bind(obj)
      g()
      // => 'obj'

    @param object Object to bind this function to
    @return Function bound to object
    @addon
    */
  Function.prototype.bind = function(object) {
    var t = this
    return function() {
      return t.apply(object, arguments)
    }
  }
}

if (!Array.prototype.last) {
  /**
    Returns the last element of the array.

    @return The last element of the array
    @addon
    */
  Array.prototype.last = function() {
    return this[this.length-1]
  }
}
if (!Array.prototype.indexOf) {
  /**
    Returns the index of obj if it is in the array.
    Returns -1 otherwise.

    @param obj The object to find from the array.
    @return The index of obj or -1 if obj isn't in the array.
    @addon
    */
  Array.prototype.indexOf = function(obj) {
    for (var i=0; i<this.length; i++)
      if (obj == this[i]) return i
    return -1
  }
}
/**
  Iterate function f over each element of the array and return an array
  of the return values.

  @param f Function to apply to each element
  @return An array of return values from applying f on each element of the array
  @type Array
  @addon
  */
Array.prototype.map = function(f) {
  var na = new Array(this.length)
  if (f)
    for (var i=0; i<this.length; i++) na[i] = f(this[i], i, this)
  else
    for (var i=0; i<this.length; i++) na[i] = this[i]
  return na
}
Array.prototype.forEach = function(f) {
  for (var i=0; i<this.length; i++) f(this[i], i, this)
}
if (!Array.prototype.reduce) {
  Array.prototype.reduce = function(f, s) {
    var i = 0
    if (arguments.length == 1) {
      s = this[0]
      i++
    }
    for(; i<this.length; i++) {
      s = f(s, this[i], i, this)
    }
    return s
  }
}
if (!Array.prototype.find) {
  Array.prototype.find = function(f) {
    for(var i=0; i<this.length; i++) {
      if (f(this[i], i, this)) return this[i]
    }
  }
}

if (!String.prototype.capitalize) {
  /**
    Returns a copy of this string with the first character uppercased.

    @return Capitalized version of the string
    @type String
    @addon
    */
  String.prototype.capitalize = function() {
    return this.replace(/^./, this.slice(0,1).toUpperCase())
  }
}

if (!String.prototype.escape) {
  /**
    Returns a version of the string that can be used as a string literal.

    @return Copy of string enclosed in double-quotes, with double-quotes
            inside string escaped.
    @type String
    @addon
    */
  String.prototype.escape = function() {
    return '"' + this.replace(/"/g, '\\"') + '"'
  }
}
if (!String.prototype.splice) {
  String.prototype.splice = function(start, count, replacement) {
    return this.slice(0,start) + replacement + this.slice(start+count)
  }
}
if (!String.prototype.strip) {
  /**
    Returns a copy of the string with preceding and trailing whitespace
    removed.

    @return Copy of string sans surrounding whitespace.
    @type String
    @addon
    */
  String.prototype.strip = function() {
    return this.replace(/^\s+|\s+$/g, '')
  }
}

if (!window['$']) {
  $ = function(id) {
    return document.getElementById(id)
  }
}

if (!Math.sinh) {
  /**
    Returns the hyperbolic sine of x.

    @param x The value for x
    @return The hyperbolic sine of x
    @addon
    */
  Math.sinh = function(x) {
    return 0.5 * (Math.exp(x) - Math.exp(-x))
  }
  /**
    Returns the inverse hyperbolic sine of x.

    @param x The value for x
    @return The inverse hyperbolic sine of x
    @addon
    */
  Math.asinh = function(x) {
    return Math.log(x + Math.sqrt(x*x + 1))
  }
}
if (!Math.cosh) {
  /**
    Returns the hyperbolic cosine of x.

    @param x The value for x
    @return The hyperbolic cosine of x
    @addon
    */
  Math.cosh = function(x) {
    return 0.5 * (Math.exp(x) + Math.exp(-x))
  }
  /**
    Returns the inverse hyperbolic cosine of x.

    @param x The value for x
    @return The inverse hyperbolic cosine of x
    @addon
    */
  Math.acosh = function(x) {
    return Math.log(x + Math.sqrt(x*x - 1))
  }
}

/**
  Creates and configures a DOM element.

  The tag of the element is given by name.

  If params is a string, it is used as the innerHTML of the created element.
  If params is a DOM element, it is appended to the created element.
  If params is an object, it is treated as a config object and merged
  with the created element.

  If params is a string or DOM element, the third argument is treated
  as the config object.

  Special attributes of the config object:
    * content
      - if content is a string, it is used as the innerHTML of the
        created element
      - if content is an element, it is appended to the created element
    * style
      - the style object is merged with the created element's style

  @param {String} name The tag for the created element
  @param params The content or config for the created element
  @param config The config for the created element if params is content
  @return The created DOM element
  */
E = function(name) {
  var el = document.createElement(name);
  for (var i=1; i<arguments.length; i++) {
    var params = arguments[i];
    if (typeof(params) == 'string') {
      el.innerHTML += params;
    } else if (params.DOCUMENT_NODE) {
      el.appendChild(params);
    } else if (params.length) {
      for (var j=0; j<params.length; j++) {
        var p = params[j];
        if (params.DOCUMENT_NODE)
          el.appendChild(p);
        else
          el.innerHTML += p;
      }
    } else {
      if (params.style) {
        var style = params.style;
        params = Object.clone(params);
        delete params.style;
        Object.forceExtend(el.style, style);
      }
      if (params.content) {
        if (typeof(params.content) == 'string') {
          el.appendChild(T(params.content));
        } else {
          var a = params.content;
          if (!a.length) a = [a];
          a.forEach(function(p){ el.appendChild(p); });
        }
        params = Object.clone(params)
        delete params.content
      }
      Object.forceExtend(el, params)
    }
  }
  return el;
}
// Safari requires each canvas to have a unique id.
E.lastCanvasId = 0
/**
  Creates and returns a canvas element with width w and height h.

  @param {int} w The width for the canvas
  @param {int} h The height for the canvas
  @param config Optional config object to pass to E()
  @return The created canvas element
  */
E.canvas = function(w,h,config) {
  var id = 'canvas-uuid-' + E.lastCanvasId
  E.lastCanvasId++
  if (!config) config = {}
  return E('canvas', Object.extend(config, {id: id, width: w, height: h}))
}

E.make = function(tagName){
  return (function() {
    var args = [tagName];
    for (var i=0; i<arguments.length; i++) args.push(arguments[i]);
    return E.apply(E, args);
  });
}
E.tags = "a abbr acronym address area audio b base bdo big blockquote body br button canvas caption center cite code col colgroup dd del dfn div dl dt em fieldset form frame frameset h1 h2 h3 h4 h5 h6 head hr html i iframe img input ins kbd labeel legend li link map meta noframes noscript object ol optgroup option p param pre q s samp script select small span strike strong style sub sup table tbody td textarea tfoot th thead title tr tt u ul var video".toUpperCase().split(" ");
(function() {
  E.tags.forEach(function(t) {
    window[t] = E[t] = E.make(t);
  });
  var makeInput = function(t) {
    return (function(value) {
      var args = [{type: t}];
      var i = 0;
      if (typeof(value) == 'string') {
        args[0].value = value;
        i++;
      }
      for (; i<arguments.length; i++) args.push(arguments[i]);
      return E.INPUT.apply(E, args);
    });
  };
  var inputs = ['SUBMIT', 'TEXT', 'RESET', 'HIDDEN', 'CHECKBOX'];
  inputs.forEach(function(t) {
    window[t] = E[t] = makeInput(t);
  });
})();

/**
  Creates a cropped version of an image.
  Does the cropping by putting the image inside a DIV and using CSS
  to crop the image to the wanted rectangle.

  @param image The image element to crop.
  @param {int} x The left side of the crop box.
  @param {int} y The top side of the crop box.
  @param {int} w The width of the crop box.
  @param {int} h The height of the crop box.
  */
E.cropImage = function(image, x, y, w, h) {
  var i = image.cloneNode(false)
  Object.forceExtend(i.style, {
    position: 'relative',
    left: -x + 'px',
    top : -y + 'px',
    margin: '0px',
    padding: '0px',
    border: '0px'
  })
  var e = E('div', {style: {
    display: 'block',
    width: w + 'px',
    height: h + 'px',
    overflow: 'hidden'
  }})
  e.appendChild(i)
  return e
}

/**
  Shortcut for document.createTextNode.

  @param {String} text The text for the text node
  @return The created text node
  */
T = function(text) {
  return document.createTextNode(text)
}

/**
  Merges the src object's attributes with the dst object, preserving all dst
  object's current attributes.

  @param dst The destination object
  @param src The source object
  @return The dst object
  @addon
  */
Object.conditionalExtend = function(dst, src) {
  for (var i in src) {
    if (dst[i] == null)
      dst[i] = src[i]
  }
  return dst
}

/**
  Creates and returns a shallow copy of the src object.

  @param src The source object
  @return A clone of the src object
  @addon
  */
Object.clone = function(src) {
  if (!src || src == true)
    return src
  switch (typeof(src)) {
    case 'string':
      return Object.extend(src+'', src)
      break
    case 'number':
      return src
      break
    case 'function':
      obj = eval(src.toSource())
      return Object.extend(obj, src)
      break
    case 'object':
      if (src instanceof Array) {
        return Object.extend([], src)
      } else {
        return Object.extend({}, src)
      }
      break
  }
}

/**
  Creates and returns an Image object, with source URL set to src and
  onload handler set to onload.

  @param {String} src The source URL for the image
  @param {Function} onload The onload handler for the image
  @return The created Image object
  @type {Image}
  */
Object.loadImage = function(src, onload) {
  var img = new Image()
  if (onload)
    img.onload = onload
  img.src = src
  return img
}

/**
  Returns true if image is fully loaded and ready for use.

  @param image The image to check
  @return Whether the image is loaded or not
  @type {boolean}
  @addon
  */
Object.isImageLoaded = function(image) {
  if (image.tagName == 'CANVAS') return true
  if (!image.complete) return false
  if (image.naturalWidth != null && image.naturalWidth == 0) return false
  if (image.width == null || image.width == 0) return false
  return true
}

/**
  Sums two objects.
  */
Object.sum = function(a,b) {
  if (a instanceof Array) {
    if (b instanceof Array) {
      var ab = []
      for (var i=0; i<a.length; i++) {
        ab[i] = a[i] + b[i]
      }
      return ab
    } else {
      return a.map(function(v){ return v + b })
    }
  } else if (b instanceof Array) {
    return b.map(function(v){ return v + a })
  } else {
    return a + b
  }
}

/**
  Substracts b from a.
  */
Object.sub = function(a,b) {
  if (a instanceof Array) {
    if (b instanceof Array) {
      var ab = []
      for (var i=0; i<a.length; i++) {
        ab[i] = a[i] - b[i]
      }
      return ab
    } else {
      return a.map(function(v){ return v - b })
    }
  } else if (b instanceof Array) {
    return b.map(function(v){ return a - v })
  } else {
    return a - b
  }
}

/**
  Deletes all attributes from an object.
  */
Object.clear = function(obj) {
  for (var i in obj) delete obj[i]
  return obj
}

if (!window.Mouse) Mouse = {}
/**
  Returns the coordinates for a mouse event relative to element.
  Element must be the target for the event.

  @param element The element to compare against
  @param event The mouse event
  @return An object of form {x: relative_x, y: relative_y}
  */
Mouse.getRelativeCoords = function(element, event) {
  var xy = {x:0, y:0}
  var osl = 0
  var ost = 0
  var el = element
  while (el) {
    osl += el.offsetLeft
    ost += el.offsetTop
    el = el.offsetParent
  }
  xy.x = event.pageX - osl
  xy.y = event.pageY - ost
  return xy
}

Browser = (function(){
  var ua = window.navigator.userAgent
  var chrome = ua.match(/Chrome\/\d+/)
  var safari = ua.match(/Safari/)
  var mobile = ua.match(/Mobile/)
  var webkit = ua.match(/WebKit\/\d+/)
  var khtml = ua.match(/KHTML/)
  var gecko = ua.match(/Gecko/)
  var ie = ua.match(/Explorer/)
  if (chrome) return 'Chrome'
  if (mobile && safari) return 'Mobile Safari'
  if (safari) return 'Safari'
  if (webkit) return 'Webkit'
  if (khtml) return 'KHTML'
  if (gecko) return 'Gecko'
  if (ie) return 'IE'
  return 'UNKNOWN'
})()


Mouse.LEFT = 0
Mouse.MIDDLE = 1
Mouse.RIGHT = 2

if (Browser == 'IE') {
  Mouse.LEFT = 1
  Mouse.MIDDLE = 4
}

Mouse.state = {}
window.addEventListener('mousedown', function(ev) {
  Mouse.state[ev.button] = true
}, true)
window.addEventListener('mouseup', function(ev) {
  Mouse.state[ev.button] = false
}, true)


Event = {
  cancel : function(event) {
    if (event.preventDefault) event.preventDefault()
  },

  stop : function(event) {
    Event.cancel(event)
    if (event.stopPropagation) event.stopPropagation()
  }
}


Key = {
  matchCode : function(event, code) {
    if (typeof code == 'string')
      code = code.toUpperCase().charCodeAt(0);
    return (
      event.which == code ||
      event.keyCode == code ||
      event.charCode == code
    );
  },

  match : function(event, key) {
    for (var i=1; i<arguments.length; i++) {
      if (arguments[i].length != null && typeof arguments[i] != 'string') {
        for (var j=0; j<arguments[i].length; j++) {
          if (Key.matchCode(event, arguments[i][j])) return true;
        }
      } else {
        if (Key.matchCode(event, arguments[i])) return true;
      }
    }
    return false;
  },

  isNumber : function(event, key) {
    var k = event.which || event.keyCode || event.charCode;
    return k >= Key.N_0 && k <= Key.N_9;
  },

  number : function(event, key) {
    var k = event.which || event.keyCode || event.charCode;
    if (k < Key.N_0 || k > Key.N_9) return NaN;
    return k - Key.N_0;
  },

  getString : function(event) {
    var k = event.which || event.keyCode || event.charCode;
    return String.fromCharCode(k);
  },

  N_0: 48,
  N_1: 49,
  N_2: 50,
  N_3: 51,
  N_4: 52,
  N_5: 53,
  N_6: 54,
  N_7: 55,
  N_8: 56,
  N_9: 57,

  BACKSPACE: 8,
  TAB: 9,
  ENTER: 13,
  ESC: 27,
  SPACE: 32,
  PAGE_UP: 33,
  PAGE_DOWN: 34,
  END: 35,
  HOME: 36,
  LEFT: 37,
  UP: 38,
  RIGHT: 39,
  DOWN: 40,
  INSERT: 45,
  DELETE: 46
}


Query = {
  parse : function(params) {
    var obj = {}
    if (!params) return obj
    params.split("&").forEach(function(p){
      var kv = p.replace(/\+/g, " ").split("=").map(decodeURIComponent)
      obj[kv[0]] = kv[1]
    })
    return obj
  },

  build : function(query) {
    if (typeof query == 'string') return encodeURIComponent(query)
    if (query instanceof Array) {
      a = query
    } else {
      var a = []
      for (var i in query) {
        if (query[i] != null)
          a.push([i, query[i]])
      }
    }
    return a.map(function(p){ return p.map(encodeURIComponent).join("=") }).join("&")
  }
}

URL = {
  build : function(base, params, fragment) {
    return base + (params != null ? '?'+Query.build(params) : '') +
                  (fragment != null ? '#'+Query.build(fragment) : '')
  },

  parse : function(url) {
    var gf = url.split("#");
    var gp = gf[0].split("?");
    var base = gp[0];
    var pr = base.split("://");
    var protocol = pr[0];
    var path = pr[1] || pr[0];
    return {
      base: base,
      path: path,
      protocol: protocol,
      query: Query.parse(gp[1]),
      fragment: gf[1],
      build: URL.__build__
    };
  },

  __build__ : function() {
    return URL.build(this.base, this.query, this.fragment)
  }

}

//SCENE_GRAPH/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

Magi.Node = Klass({
	  model : null,
	  position : null,
	  rotation : null,
	  scaling : null,
	  polygonOffset : null,
	  scaleAfterRotate : false,
	  depthMask : null,
	  depthTest : null,
	  display : true,
	  transparent : false,
	  id : null,
	  
	  destroy : function() {
		 
		  delete model;
		  //delete this.childNodes;

	},

	  initialize : function(model) {
	    this.model = model;
	    this.renderPasses = {normal: true};
	    this.material = new Magi.Material();
	    this.matrix = mat4.newIdentity();
	    this.normalMatrix = mat3.newIdentity();
	    this.rotation = {angle : 0, axis : vec3.create([0,1,0])};
	    this.position = vec3.create([0, 0, 0]);
	    this.scaling = vec3.create([1, 1, 1]);
	    this.frameListeners = [];
	    this.childNodes = [];
	  },

	  getNodeById : function(name) {
	    var found = null;
	    try {
	      this.filterNodes(function(n){ if (n.id == name) { found=n; throw(null); } });
	    } catch(e) {
	      return found;
	    }
	  },

	  getNodesById : function(name) {
	    return this.filterNodes(function(n){ return (n.id == name); });
	  },

	  getNodesByKlass : function(klass) {
	    return this.filterNodes(function(n){ return (n instanceof klass); });
	  },

	  getNodesByMethod : function(name) {
	    return this.filterNodes(function(n){ return n[name]; });
	  },

	  getNodesByKeyValue : function(key, value) {
	    return this.filterNodes(function(n){ return n[key] == value; });
	  },

	  filterNodes : function(f) {
	    var nodes = [];
	    this.forEach(function(n) {
	      if (f(n)) nodes.push(n);
	    });
	    return nodes;
	  },

	  forEach : function(f) {
	    f.call(this,this);
	    this.childNodes.forEach(function(cn){
	      cn.forEach(f);
	    });
	  },

	  setX : function(x) {
	    this.position[0] = x;
	    return this;
	  },

	  setY : function(x) {
	    this.position[1] = x;
	    return this;
	  },
	  
	  setZ : function(x) {
	    this.position[2] = x;
	    return this;
	  },

	  setPosition : function(x,y,z) {
	    if (x.length != null) {
	      vec3.set(x, this.position);
	    } else {
	      if (y == null) {
	        vec3.set3(x, this.position)
	      } else {
	        this.position[0] = x;
	        this.position[1] = y;
	        if (z != null)
	          this.position[2] = z;
	      }
	    }
	    return this;
	  },

	  setScale : function(x,y,z) {
	    if (x.length != null) {
	      vec3.set(x, this.scaling);
	    } else {
	      if (y == null) {
	        vec3.set3(x, this.scaling)
	      } else {
	        this.scaling[0] = x;
	        this.scaling[1] = y;
	        if (z != null)
	          this.scaling[2] = z;
	      }
	    }
	    return this;
	  },

	  setAngle : function(a) {
	    this.rotation.angle = a;
	    return this;
	  },

	  setAxis : function(x,y,z) {
	    if (x.length != null) {
	      vec3.set(x, this.rotation.axis);
	    } else {
	      if (y == null) {
	        vec3.set3(x, this.rotation.axis)
	      } else {
	        this.rotation.axis[0] = x;
	        this.rotation.axis[1] = y;
	        if (z != null)
	          this.rotation.axis[2] = z;
	      }
	    }
	    return this;
	  },

	  draw : function(gl, state, perspectiveMatrix) {
	    if (!this.model || !this.display) return;
	    if (this.material) {
	      this.material.apply(gl, state, perspectiveMatrix, this.matrix, this.normalMatrix);
	    }
	    if (this.model.gl == null) this.model.gl = gl;
	    var psrc = state.blendFuncSrc;
	    var pdst = state.blendFuncDst;
	    var dm = state.depthMask;
	    var dt = state.depthTest;
	    var poly = state.polygonOffset;
	    var bl = state.blend;
	    if (this.polygonOffset) {
	      gl.polygonOffset(this.polygonOffset.factor, this.polygonOffset.units);
	    }
	    if (this.depthMask != null && this.depthMask != state.depthMask) {
	      gl.depthMask(this.depthMask);
	    }
	    if (this.depthTest != null && this.depthTest != state.depthTest) {
	      if (this.depthTest)
	        gl.enable(gl.DEPTH_TEST);
	      else
	        gl.disable(gl.DEPTH_TEST);
	    }
	    if (this.blendFuncSrc && this.blendFuncDst) {
	      gl.blendFunc(gl[this.blendFuncSrc], gl[this.blendFuncDst]);
	    }
	    if (this.blend != null && this.blend != state.blend) {
	      if (this.blend) gl.enable(gl.BLEND);
	      else gl.disable(gl.BLEND);
	    }

	    this.model.draw(
	      state.currentShader.attrib('Vertex'),
	      state.currentShader.attrib('Normal'),
	      state.currentShader.attrib('TexCoord')
	    );

	    if (this.blend != null && this.blend != state.blend) {
	      if (bl) gl.enable(gl.BLEND);
	      else gl.disable(gl.BLEND);
	    }
	    if (this.blendFuncSrc && this.blendFuncDst) {
	      gl.blendFunc(gl[psrc], gl[pdst]);
	    }
	    if (this.depthTest != null && this.depthTest != state.depthTest) {
	      if (dt)
	        gl.enable(gl.DEPTH_TEST);
	      else
	        gl.disable(gl.DEPTH_TEST);
	    }
	    if (this.depthMask != null && this.depthMask != state.depthMask) {
	      gl.depthMask(dm);
	    }
	    if (this.polygonOffset) {
	      gl.polygonOffset(poly.factor, poly.units);
	    }
	  },
	  
	  addFrameListener : function(f) {
	    this.frameListeners.push(f);
	  },
	  
	  update : function(t, dt) {
	    var a = [];
	    for (var i=0; i<this.frameListeners.length; i++) {
	      a.push(this.frameListeners[i]);
	    }
	    for (var i=0; i<a.length; i++) {
	      if (this.frameListeners.indexOf(a[i]) != -1)
	        a[i].call(this, t, dt, this);
	    }
	    for (var i=0; i<this.childNodes.length; i++)
	      this.childNodes[i].update(t, dt);
	  },
	  
	  appendChild : function(c) {
	    this.childNodes.push(c);
	  },
	  
	  updateTransform : function(matrix) {
	    var m = this.matrix;
	    mat4.set(matrix, m);
	    var p = this.position;
	    var s = this.scaling;
	    var doScaling = (s[0] != 1) || (s[1] != 1) || (s[2] != 1);
	    if (p[0] || p[1] || p[2])
	      mat4.translate(m, p);
	    if (this.scaleAfterRotate && doScaling)
	      mat4.scale(m, s);
	    if (this.rotation.angle != 0)
	      mat4.rotate(m, this.rotation.angle, this.rotation.axis);
	    if (!this.scaleAfterRotate && doScaling)
	      mat4.scale(m, s);
	    if (this.isBillboard)
	      mat4.billboard(m);
	    mat4.toInverseMat3(m, this.normalMatrix);
	    mat3.transpose(this.normalMatrix);
	    for (var i=0; i<this.childNodes.length; i++)
	      this.childNodes[i].updateTransform(m);
	  },
	  
	  collectDrawList : function(arr) {
	    if (!arr) arr = [];
	    if (this.display) {
	      arr.push(this);
	      for (var i=0; i<this.childNodes.length; i++)
	        this.childNodes[i].collectDrawList(arr);
	    }
	    return arr;
	  }
	});

	Magi.Material = Klass({
	  initialize : function(shader) {
	    this.shader = shader;
	    this.textures = {};
	    for (var i in this.textures) delete this.textures[i];
	    this.floats = {};
	    for (var i in this.floats) delete this.floats[i];
	    this.ints = {};
	    for (var i in this.ints) delete this.ints[i];
	  },

	  copyValue : function(v){
	    if (typeof v == 'number') return v;
	    var a = [];
	    for (var i=0; i<v.length; i++) a[i] = v[i];
	    return a;
	  },
	  
	  copy : function(){
	    var m = new Magi.Material();
	    for (var i in this.floats) m.floats[i] = this.copyValue(this.floats[i]);
	    for (var i in this.ints) m.ints[i] = this.copyValue(this.ints[i]);
	    for (var i in this.textures) m.textures[i] = this.textures[i];
	    m.shader = this.shader;
	    return m;
	  },
	  
	  apply : function(gl, state, perspectiveMatrix, matrix, normalMatrix) {
	    var shader = this.shader;
	    if (shader && shader.gl == null) shader.gl = gl;
	    if (state.currentShader != shader) {
	      shader.use()
	      shader.uniformMatrix4fv("PMatrix", perspectiveMatrix);
	      Magi.Stats.uniformSetCount++;
	      state.currentShader = this.shader;
	      Magi.Stats.shaderBindCount++;
	    }
	    state.currentShader.uniformMatrix4fv("MVMatrix", matrix);
	    state.currentShader.uniformMatrix3fv("NMatrix", normalMatrix);
	    Magi.Stats.uniformSetCount += 2;
	    if (state.currentMaterial == this) return;
	    state.currentMaterial = this;
	    Magi.Stats.materialUpdateCount++;
	    this.applyTextures(gl, state);
	    this.applyFloats();
	    this.applyInts();
	  },
	  
	  applyTextures : function(gl, state) {
	    var texUnit = 0;
	    for (var name in this.textures) {
	      var tex = this.textures[name];
	      if (!tex) tex = Magi.Texture.getDefaultTexture(gl);
	      if (tex.gl == null) tex.gl = gl;
	      if (state.textures[texUnit] != tex) {
	        state.textures[texUnit] = tex;
	        gl.activeTexture(gl.TEXTURE0+texUnit);
	        tex.use();
	        Magi.Stats.textureSetCount++;
	      }
	      this.shader.uniform1i(name, texUnit);
	      Magi.Stats.uniformSetCount++;
	      ++texUnit;
	    }
	  },

	  cmp : function(a, b) {
	    var rv = false;
	    if (a && b && a.length && b.length && a.length === b.length) {
	      rv = true;
	      for (var i=0; i<a.length; i++)
	        rv = rv && (a[i] === b[i]);
	    }
	    return rv;
	  },
	  
	  applyFloats : function() {
	    var shader = this.shader;
	    for (var name in this.floats) {
	      var uf = this.floats[name];
	      var s = shader.uniform(name);
	      if (s.current === uf || this.cmp(s.current,uf))
	        continue;
	      s.current = uf;
	      Magi.Stats.uniformSetCount++;
	      if (uf.length == null) {
	        shader.uniform1f(name, uf);
	      } else {
	        switch (uf.length) {
	          case 4:
	            shader.uniform4fv(name, uf);
	            break;
	          case 3:
	            shader.uniform3fv(name, uf);
	            break;
	          case 16:
	            shader.uniformMatrix4fv(name, uf);
	            break;
	          case 9:
	            shader.uniformMatrix3fv(name, uf);
	            break;
	          case 2:
	            shader.uniform2fv(name, uf);
	            break;
	          default:
	            shader.uniform1fv(name, uf);
	        }
	      }
	    }
	  },
	  
	  applyInts : function() {
	    var shader = this.shader;
	    for (var name in this.ints) {
	      var uf = this.ints[name];
	      var s = shader.uniform(name);
	      if (s.current === uf || this.cmp(s.current,uf))
	        continue;
	      s.current = uf;
	      Magi.Stats.uniformSetCount++;
	      if (uf.length == null) {
	        shader.uniform1i(name, uf);
	      } else {
	        switch (uf.length) {
	          case 4:
	            shader.uniform4iv(name, uf);
	            break;
	          case 3:
	            shader.uniform3iv(name, uf);
	            break;
	          case 2:
	            shader.uniform2iv(name, uf);
	            break;
	          default:
	            shader.uniform1iv(name, uf);
	        }
	      }
	    }
	  }
	  
	});

	Magi.GLDrawState = Klass({
	  textures : null,
	  currentMaterial : null,
	  currentShader : null,
	  polygonOffset : null,
	  blendFuncSrc : 'ONE',
	  blendFuncDst : 'ONE_MINUS_SRC_ALPHA',
	  depthMask : true,
	  depthTest : true,
	  blend : true,
	  
	  initialize: function(){
	    this.polygonOffset = {factor: 0, units: 0},
	    this.textures = [];
	  }
	});

	Magi.Camera = Klass({
	  fov : 30,
	  targetFov : 30,
	  zNear : 1,
	  zFar : 10000,
	  useLookAt : true,
	  ortho : false,
	  stereo : false,
	  stereoSeparation : 0.025,
	  renderPass : 'normal',
	  blend : true,
	  blendFuncSrc : 'ONE',
	  blendFuncDst : 'ONE_MINUS_SRC_ALPHA',

	  initialize : function() {
	    this.position = vec3.create([5,5,5]);
	    this.lookAt = vec3.create([0,0,0]);
	    this.up = vec3.create([0,1,0]);
	    this.matrix = mat4.create();
	    this.perspectiveMatrix = mat4.create();
	    this.frameListeners = [];
	  },
	  
	  addFrameListener : Magi.Node.prototype.addFrameListener,

	  update : function(t, dt) {
	    var a = [];
	    for (var i=0; i<this.frameListeners.length; i++) {
	      a.push(this.frameListeners[i]);
	    }
	    for (var i=0; i<a.length; i++) {
	      if (this.frameListeners.indexOf(a[i]) != -1)
	        a[i].call(this, t, dt, this);
	    }
	    if (this.targetFov && this.fov != this.targetFov)
	      this.fov += (this.targetFov - this.fov) * (1-Math.pow(0.7, (dt/30)));
	  },

	  getLookMatrix : function() {
	    if (this.useLookAt)
	      mat4.lookAt(this.position, this.lookAt, this.up, this.matrix);
	    else
	      mat4.identity(this.matrix);
	    return this.matrix;
	  },

	  drawViewport : function(gl, x, y, width, height, scene) {
	    gl.enable(gl.SCISSOR_TEST);
	    gl.viewport(x,y,width,height);
	    gl.scissor(x,y,width,height);
	    if (this.ortho) {
	      mat4.ortho(x, width, -height, -y, this.zNear, this.zFar, this.perspectiveMatrix);
	    } else {
	      mat4.perspective(this.fov, width/height, this.zNear, this.zFar, this.perspectiveMatrix);
	    }
	    scene.updateTransform(this.getLookMatrix());
	    var st = new Magi.GLDrawState();
	    this.resetState(gl, st);

	    var t = new Date();
	    var drawList = scene.collectDrawList();
	    var transparents = [];
	    for (var i=0; i<drawList.length; i++) {
	      var d = drawList[i];
	      if (!d.renderPasses[this.renderPass])
	        continue;
	      if (d.transparent) {
	        transparents.push(d);
	      } else {
	        d.draw(gl, st, this.perspectiveMatrix);
	      }
	    }
	    
	    this.normalDrawTime = new Date() - t;
	    transparents.stableSort(function(a,b) {
	      return a.matrix[14] - b.matrix[14];
	    });

	    var st = new Magi.GLDrawState();
	    this.resetState(gl, st);

	    gl.depthMask(false);
	    st.depthMask = false;
	    
	    for (var i=0; i<transparents.length; i++) {
	      var d = transparents[i];
	      d.draw(gl, st, this.perspectiveMatrix);
	    }
	    gl.depthMask(true);
	    this.transparentDrawTime = new Date() - t - this.normalDrawTime;
	    gl.disable(gl.SCISSOR_TEST);
	    this.drawTime = new Date() - t;

	  },

	  resetState : function(gl, st) {
	    gl.depthFunc(gl.LESS);
	    gl.disable(gl.CULL_FACE);
	    gl.cullFace(gl.BACK);
	    gl.frontFace(gl.CCW);
	    gl.enable(gl.DEPTH_TEST);
	    st.depthTest = true;
	    if (this.blendFuncSrc && this.blendFuncDst) {
	      st.blendFuncSrc = this.blendFuncSrc;
	      st.blendFuncDst = this.blendFuncDst;
	      gl.blendFunc(gl[this.blendFuncSrc], gl[this.blendFuncDst]);
	    }
	    if (this.blend) {
	      gl.enable(gl.BLEND);
	    } else {
	      gl.disable(gl.BLEND);
	    }
	    st.blend = this.blend;
	    gl.depthMask(true);
	    st.depthMask = true;
	  },
	  
	  draw : function(gl, width, height, scene) {
	    if (this.stereo) {
	      var p = vec3.create(this.position);
	      var sep = vec3.create();
	      vec3.subtract(this.lookAt, p, sep)
	      vec3.cross(this.up, sep, sep);
	      vec3.scale(sep, this.stereoSeparation/2, sep);

	      vec3.subtract(p, sep, this.position);
	      this.drawViewport(gl, 0, 0, width/2, height, scene);
	      
	      vec3.add(p, sep, this.position);
	      this.drawViewport(gl, width/2, 0, width/2, height, scene);

	      vec3.set(p, this.position);
	    } else {
	      this.drawViewport(gl, 0, 0, width, height, scene);
	    }
	  }
	});

//SCENE_UTIL/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	Magi.Scene = Klass({
		  frameDuration : 13,
		  time : 0,
		  timeDir : 1,
		  timeSpeed : 1,
		  previousTime : 0,
		  frameTimes : [],

		  fpsCanvas : null,

		  bg : [1,1,1,1],
		  clear : true,

		  paused : false,
		  showStats : false,
		  
		  initialize : function(canvas, scene, cam, args) {
		    if (!scene) scene = new Magi.Node();
		    if (!cam) cam = Magi.Scene.getDefaultCamera();
		    this.canvas = canvas;
		    var defaultArgs = {
		      alpha: true, depth: true, stencil: true, antialias: true,
		      premultipliedAlpha: true
		    };
		    if (args)
		      Object.extend(defaultArgs, args);
		    this.gl = Magi.getGLContext(canvas, defaultArgs);
		    this.clearBits = this.gl.COLOR_BUFFER_BIT |
		                     this.gl.DEPTH_BUFFER_BIT |
		                     this.gl.STENCIL_BUFFER_BIT;
		    this.scene = scene;
		    this.root = scene;
		    this.camera = cam;
		    this.mouse = {
		      x : 0,
		      y : 0,
		      pressure : 1.0,
		      tiltX : 0.0,
		      tiltY : 0.0,
		      deviceType : 0,
		      left: false,
		      middle: false,
		      right: false
		    };
		    this.setupEventListeners();
		    this.startFrameLoop();
		  },
		  
		  destroyStuff : function(canvas) {
			  canvas.width = canvas.height;
			  this.scene = null;
			  this.root = null;
			  var defaultArgs = {
				      alpha: true, depth: true, stencil: true, antialias: true,
				      premultipliedAlpha: true
				    };
			  this.gl = Magi.getGLContext(canvas, defaultArgs);
			  this.gl.clearColor(0.0, 0.0, 0.0, 1.0);                     
			  this.gl.clearDepth(1.0);                                     
			  this.gl.clear(this.gl.COLOR_BUFFER_BIT | this.gl.DEPTH_BUFFER_BIT);
			 
		},
		
		getCanvasData : function(canvas) {
			return canvas.toDataURL();
			
			
		},
		
		  getDefaultCamera : function() {
		    var cam = new Magi.Camera();
		    vec3.set([0, 1.0, 0], cam.lookAt);
		    vec3.set([Math.cos(1)*7, 3, Math.sin(1)*7], cam.position);
		    cam.fov = 45;
		    cam.angle = 1;
		    return cam;
		  },
		  
		  startFrameLoop : function() {
		    this.previousTime = new Date;
		    clearInterval(this.drawInterval);
		    var t = this;
		    this.drawInterval = setInterval(function(){ t.draw(); }, this.frameDuration);
		  },

		  updateMouse : function(ev) {
		    this.mouse.deviceType = ev.mozDeviceType || 0;
		    this.mouse.tiltX = ev.mozTiltX || 0;
		    this.mouse.tiltY = ev.mozTiltY || 0;
		    this.mouse.pressure = ev.mozPressure || 0;
		    this.mouse.x = ev.clientX;
		    this.mouse.y = ev.clientY;
		  },
		  
		  setupEventListeners : function() {
		    var t = this;
		    window.addEventListener('mousedown',  function(ev){
		      switch (ev.button) {
		      case Mouse.LEFT:
		        t.mouse.left = true; break;
		      case Mouse.RIGHT:
		        t.mouse.right = true; break;
		      case Mouse.MIDDLE:
		        t.mouse.middle = true; break;
		      }
		      t.updateMouse(ev);
		    }, false);
		    window.addEventListener('mouseup', function(ev) {
		      switch (ev.button) {
		      case Mouse.LEFT:
		        t.mouse.left = false; break;
		      case Mouse.RIGHT:
		        t.mouse.right = false; break;
		      case Mouse.MIDDLE:
		        t.mouse.middle = false; break;
		      }
		      t.updateMouse(ev);
		    }, false);
		    window.addEventListener('mousemove', function(ev) {
		      t.updateMouse(ev);
		    }, false);
		  },

		  draw : function() {
		    if (this.paused) return;
		    var newTime = new Date;
		    var real_dt = newTime - this.previousTime;
		    var dt = this.timeDir * this.timeSpeed * real_dt;
		    this.time += dt;
		    this.previousTime = newTime;
		    this.frameTime = real_dt;
		    
		    this.camera.update(this.time, dt);
		    this.scene.update(this.time, dt);

		    var t = new Date();
		    this.updateTime = t - newTime;

		    if (this.drawOnlyWhenChanged && !this.changed) return;

		    if (this.clear) {
		      this.gl.depthMask(true);
		      this.gl.clearColor(this.bg[0], this.bg[1], this.bg[2], this.bg[3]);
		      this.gl.clear(this.clearBits);
		    }

		    this.camera.draw(this.gl, this.canvas.width, this.canvas.height, this.root);
		    this.gl.flush();

		    this.drawTime = new Date() - t;

		    this.updateFps(this.frameTimes, real_dt);
		    if (!this.firstFrameDoneTime) this.firstFrameDoneTime = new Date();
		    this.changed = false;
		    Magi.throwError(this.gl, "Scene draw loop");
		    if (this.showStats) {
		      var stats = $('stats');
		      if (stats) {
		        Magi.Stats.print(stats);
		        Magi.Stats.reset();
		      }
		    }
		  },

		  updateFps : function(frames,real_dt) {
		    var fps = this.fpsCanvas || document.getElementById('fps');
		    if (!fps) return;
		    var ctx = fps.getContext('2d');
		    ctx.clearRect(0,0,fps.width,fps.height);
		    var h = fps.height;
		    frames.push(1000 / (1+real_dt));
		    if (frames.length > 1000)
		      frames.splice(200);
		    var start = Math.max(0,frames.length-200);
		    for (var i=start; i<frames.length; i++) {
		      ctx.fillRect(i-start,h,1,-frames[i]/3);
		    }
		  },

		  useDefaultCameraControls : function() {
		    var s = this;
		    var cv = this.canvas;

		    var yRot = new Magi.Node();
		    vec3.set([1,0,0], yRot.rotation.axis);

		    var xRot = new Magi.Node();
		    vec3.set([0,1,0], xRot.rotation.axis);

		    yRot.appendChild(xRot);
		    xRot.appendChild(this.scene);
		    this.root = yRot;

		    var wheelHandler = function(ev) {
		      var ds = ((ev.detail || ev.wheelDelta) > 0) ? 1.25 : (1 / 1.25);
		      if (ev.shiftKey) {
		        yRot.scaling[0] /= ds;
		        yRot.scaling[1] /= ds;
		        yRot.scaling[2] /= ds;
		      } else {
		        s.camera.targetFov *= ds;
		      }
		      s.changed = true;
		      ev.preventDefault();
		    };
		    s.camera.addFrameListener(function() {
		      if (Math.abs(this.targetFov - this.fov) > 0.01) {
		        s.changed = true;
		      }
		    });
		    cv.addEventListener('DOMMouseScroll', wheelHandler, false);
		    cv.addEventListener('mousewheel', wheelHandler, false);

		    cv.addEventListener('mousedown', function(ev){
		      s.dragging = true;
		      s.sx = ev.clientX;
		      s.sy = ev.clientY;
		      ev.preventDefault();
		    }, false);
		    window.addEventListener('mousemove', function(ev) {
		      if (s.dragging) {
		        var dx = ev.clientX - s.sx, dy = ev.clientY - s.sy;
		        s.sx = ev.clientX, s.sy = ev.clientY;
		        if (s.mouse.left) {
		        	if(ev.ctrlKey == true){
		        		yRot.position[0] += dx * 0.01 * (s.camera.fov / 45);
				        yRot.position[1] -= dy * 0.01 * (s.camera.fov / 45);
		        	}
		        	else{
			          xRot.rotation.angle += dx / 200;
			          yRot.rotation.angle += dy / 200;
			          }
		        } else if (s.mouse.middle) {
		          yRot.position[0] += dx * 0.01 * (s.camera.fov / 45);
		          yRot.position[1] -= dy * 0.01 * (s.camera.fov / 45);
		        }
		        ev.preventDefault();
		        s.changed = true;
		      }
		    }, false);
		    window.addEventListener('mouseup', function(ev) {
		      if (s.dragging) {
		        s.dragging = false;
		        ev.preventDefault();
		      }
		    }, false);
		    s.changed = true;
		  }
		});

		Magi.Motion = {
		  makeBounce : function() {
		    this.addFrameListener(function(t, dt) {
		      var y = 2*Math.abs(Math.sin(t / 500));
		      this.position[1] = y;
		    });
		    return this;
		  },

		  makeRotate : function(speed) {
		    speed = speed || 0.2;
		    this.addFrameListener(function(t,dt) {
		      this.rotation.angle = (Math.PI*2*t / (1000/speed)) % (Math.PI*2);
		    });
		    return this;
		  }
		};

		Magi.Cube = Klass(Magi.Node, Magi.Motion, {
		  initialize : function() {
		    Magi.Node.initialize.call(this, Magi.Geometry.Cube.getCachedVBO());
		    this.material = Magi.DefaultMaterial.get();
		  }
		});

		Magi.Ring = Klass(Magi.Node, Magi.Motion, {
		  initialize : function(height, angle, segments, yCount) {
		    Magi.Node.initialize.call(this,
		      Magi.Geometry.Ring.getCachedVBO(null, height, segments, yCount, angle)
		    );
		    this.material = Magi.DefaultMaterial.get();
		  }
		});

		Magi.FilterQuad = Klass(Magi.Node, {
		  identityTransform : true,
		  depthMask : false,

		  initialize : function(frag) {
		    Magi.Node.initialize.call(this, Magi.Geometry.Quad.getCachedVBO());
		    this.material = Magi.FilterQuadMaterial.make(null, frag);
		  }
		});

		Magi.ColorQuad = Klass(Magi.Node, {
		  initialize : function(r,g,b,a) {
		    Magi.Node.initialize.call(this, Magi.Geometry.Quad.getCachedVBO());
		    this.material = Magi.ColorQuadMaterial.get(null);
		    this.transparent = this.a < 1;
		    this.material.floats.Color = vec4.create([r,g,b,a]);
		  }
		});

		Magi.Alignable = {
		  leftAlign : 1,
		  rightAlign : -1,
		  topAlign : -1,
		  bottomAlign : 1,
		  centerAlign : 0,

		  align: 0,
		  valign: 0,

		  alignQuad : function(node, w, h) {
		    node.position[0] = this.align * w/2;
		    node.position[1] = this.valign * h/2;
		  },

		  updateAlign : function() {
		    this.alignQuad(this.alignedNode, this.width, this.height);
		  },

		  setAlign : function(h, v) {
		    this.align = h;
		    if (v != null)
		      this.valign = v;
		    this.updateAlign();
		    return this;
		  },

		  setVAlign : function(v) {
		    this.valign = v;
		    this.updateAlign();
		    return this;
		  }

		};

		Magi.Image = Klass(Magi.Node, Magi.Alignable, {
		  initialize : function(src) {
		    Magi.Node.initialize.call(this);
		    var tex = new Magi.Texture();
		    tex.generateMipmaps = false;
		    this.alignedNode = new Magi.Node(Magi.Geometry.Quad.getCachedVBO());
		    this.alignedNode.material = Magi.FilterMaterial.get().copy();
		    this.alignedNode.material.textures.Texture0 = tex;
		    this.alignedNode.transparent = true;
		    this.texture = tex;
		    this.appendChild(this.alignedNode);
		    this.setImage(src);
		  },

		  setImage : function(src) {
		    var image = src;
		    if (typeof src == 'string') {
		      image = new Image();
		      image.src = src;
		    }
		    this.image = image;
		    this.image.width; // workaround for strange chrome bug
		    this.width = this.image.width;
		    this.height = this.image.height;
		    this.alignedNode.scaling[0] = this.image.width / 2;
		    this.alignedNode.scaling[1] = this.image.height / 2;
		    this.updateAlign();
		    this.texture.image = this.image;
		    this.texture.changed = true;
		  }
		});

		Magi.Text = Klass(Magi.Image, Magi.Alignable, {
		  fontSize : 24,
		  font : 'Arial',
		  color : 'black',

		  initialize : function(content, fontSize, color, font) {
		    this.canvas = E.canvas(1, 1);
		    Magi.Image.initialize.call(this, this.canvas);
		    if (fontSize) this.fontSize = fontSize;
		    if (font) this.font = font;
		    if (color) this.color = color;
		    this.setText(content);
		  },

		  setText : function(text) {
		    this.text = text;
		    var ctx = this.canvas.getContext('2d');
		    var sf = this.fontSize + 'px ' + this.font;
		    ctx.font = sf;
		    var dims = ctx.measureText(text);
		    this.canvas.width = Math.max(1, Math.min(2048, dims.width));
		    this.canvas.height = Math.max(1, Math.min(2048, Math.ceil(this.fontSize*1.2)));
		    var ctx = this.canvas.getContext('2d');
		    ctx.font = sf;
		    ctx.clearRect(0,0,this.canvas.width, this.canvas.height);
		    ctx.fillStyle = this.color;
		    ctx.fillText(this.text, 0, this.fontSize);
		    this.setImage(this.canvas);
		  },

		  setFontSize : function(fontSize) {
		    this.fontSize = fontSize;
		    this.setText(this.text);
		  },
		  
		  setFont : function(font) {
		    this.font = font;
		    this.setText(this.text);
		  },

		  setColor : function(color) {
		    this.color = color;
		    this.setText(this.text);
		  },
		});

		Magi.MeshText = Klass(Magi.Text, {
		  initialize : function(content, fontSize, color, font) {
		    Magi.Text.initialize.apply(this, arguments);
		    this.alignedNode.model = Magi.Geometry.QuadMesh.getCachedVBO(null,20,100);
		  }
		});

		Magi.MeshImage = Klass(Magi.Image, {
		  initialize : function(image) {
		    Magi.Image.initialize.apply(this, arguments);
		    this.alignedNode.model = Magi.Geometry.QuadMesh.getCachedVBO();
		  }
		});

		Magi.FilterMaterial = {
		  vert : {type: 'VERTEX_SHADER', text: (
		    "precision mediump float;"+
		    "attribute vec3 Vertex;"+
		    "attribute vec2 TexCoord;"+
		    "uniform mat4 PMatrix;"+
		    "uniform mat4 MVMatrix;"+
		    "uniform mat3 NMatrix;"+
		    "varying vec2 texCoord0;"+
		    "void main()"+
		    "{"+
		    "  vec4 v = vec4(Vertex, 1.0);"+
		    "  texCoord0 = vec2(TexCoord.s, 1.0-TexCoord.t);"+
		    "  vec4 worldPos = MVMatrix * v;"+
		    "  gl_Position = PMatrix * worldPos;"+
		    "}"
		  )},

		  frag : {type: 'FRAGMENT_SHADER', text: (
		    "precision mediump float;"+
		    "uniform sampler2D Texture0;"+
		    "varying vec2 texCoord0;"+
		    "void main()"+
		    "{"+
		    "  vec4 c = texture2D(Texture0, texCoord0);"+
		    "  gl_FragColor = c*c.a;"+
		    "}"
		  )},

		  make : function(gl, fragmentShader) {
		    var shader = new Magi.Filter(null, this.vert, fragmentShader||this.frag);
		    return this.setupMaterial(shader);
		  },

		  get : function(gl) {
		    if (!this.cached)
		      this.cached = this.make(gl);
		    return this.cached.copy();
		  },

		  setupMaterial : function(shader) {
		    var m = new Magi.Material(shader);
		    m.textures.Texture0 = null;
		    return m;
		  }
		};

		Magi.FilterQuadMaterial = Object.clone(Magi.FilterMaterial);
		Magi.FilterQuadMaterial.vert = {type: 'VERTEX_SHADER', text: (
		  "precision mediump float;"+
		  "attribute vec3 Vertex;"+
		  "attribute vec2 TexCoord;"+
		  "uniform mat4 PMatrix;"+
		  "uniform mat4 MVMatrix;"+
		  "uniform mat3 NMatrix;"+
		  "varying vec2 texCoord0;"+
		  "void main()"+
		  "{"+
		  "  vec4 v = vec4(Vertex, 1.0);"+
		  "  texCoord0 = vec2(TexCoord.s, TexCoord.t);"+
		  "  gl_Position = v;"+
		  "}"
		)};

		Magi.ColorQuadMaterial = Object.clone(Magi.FilterMaterial);
		Magi.ColorQuadMaterial.vert = {type: 'VERTEX_SHADER', text: (
		  "precision mediump float;"+
		  "attribute vec3 Vertex;"+
		  "attribute vec2 TexCoord;"+
		  "uniform mat4 PMatrix;"+
		  "uniform mat4 MVMatrix;"+
		  "uniform mat3 NMatrix;"+
		  "void main()"+
		  "{"+
		  "  vec4 v = vec4(Vertex, 1.0);"+
		  "  gl_Position = v;"+
		  "}"
		)};
		Magi.ColorQuadMaterial.frag = {type: 'FRAGMENT_SHADER', text: (
		  "precision mediump float;"+
		  "uniform vec4 Color;"+
		  "void main()"+
		  "{"+
		  "  gl_FragColor = Color;"+
		  "}"
		)};

		Magi.DefaultMaterial = {
		  vert : {type: 'VERTEX_SHADER', text: (
		    "precision mediump float;"+
		    "attribute vec3 Vertex;"+
		    "attribute vec3 Normal;"+
		    "attribute vec2 TexCoord;"+
		    "uniform mat4 PMatrix;"+
		    "uniform mat4 MVMatrix;"+
		    "uniform mat3 NMatrix;"+
		    "uniform float LightConstantAtt;"+
		    "uniform float LightLinearAtt;"+
		    "uniform float LightQuadraticAtt;"+
		    "uniform vec4 LightPos;"+
		    "varying vec3 normal, lightDir, eyeVec;"+
		    "varying vec2 texCoord0;"+
		    "varying float attenuation;"+
		    "void main()"+
		    "{"+
		    "  vec3 lightVector;"+
		    "  vec4 v = vec4(Vertex, 1.0);"+
		    "  texCoord0 = vec2(TexCoord.s, 1.0-TexCoord.t);"+
		    "  normal = normalize(NMatrix * Normal);"+
		    "  vec4 worldPos = MVMatrix * v;"+
		    "  lightVector = vec3(LightPos - worldPos);"+
		    "  lightDir = normalize(lightVector);"+
		    "  float dist = length(lightVector);"+
		    "  eyeVec = -vec3(worldPos);"+
		    "  attenuation = 1.0 / (LightConstantAtt + LightLinearAtt*dist + LightQuadraticAtt * dist*dist);"+
		    "  gl_Position = PMatrix * worldPos;"+
		    "}"
		  )},

		  frag : {type: 'FRAGMENT_SHADER', text: (
		    "precision mediump float;"+
		    "uniform vec4 LightDiffuse;"+
		    "uniform vec4 LightSpecular;"+
		    "uniform vec4 LightAmbient;"+
		    "uniform vec4 MaterialSpecular;"+
		    "uniform vec4 MaterialDiffuse;"+
		    "uniform vec4 MaterialAmbient;"+
		    "uniform vec4 GlobalAmbient;"+
		    "uniform float MaterialShininess;"+
		    "uniform sampler2D DiffTex, SpecTex, EmitTex;"+
		    "varying vec3 normal, lightDir, eyeVec;"+
		    "varying vec2 texCoord0;"+
		    "varying float attenuation;"+
		    "void main()"+
		    "{"+
		    "  vec4 color = GlobalAmbient * LightAmbient * MaterialAmbient;"+
		    "  vec4 matDiff = MaterialDiffuse + texture2D(DiffTex, texCoord0);"+
		    "  vec4 matSpec = MaterialSpecular + texture2D(SpecTex, texCoord0);"+
		    "  vec4 diffuse = LightDiffuse * matDiff;"+
		    "  float lambertTerm = dot(normal, lightDir);"+
		    "  vec4 lcolor = diffuse * lambertTerm * attenuation;"+
		    "  vec3 E = normalize(eyeVec);"+
		    "  vec3 R = reflect(-lightDir, normal);"+
		    "  float specular = pow( max(dot(R, E), 0.0), MaterialShininess );"+
		    "  lcolor += matSpec * LightSpecular * specular * attenuation;"+
		    "  color += lcolor * step(0.0, lambertTerm);"+
		    "  color += texture2D(EmitTex, texCoord0);" +
		    "  color *= matDiff.a;"+
		    "  color.a = matDiff.a;"+
		    "  gl_FragColor = color;"+
		    "}"
		  )},

		  get : function(gl) {
		    if (!this.cached) {
		      var shader = new Magi.Shader(null, this.vert, this.frag);
		      this.cached = this.setupMaterial(shader);
		    }
		    return this.cached.copy();
		  },

		  setupMaterial : function(shader) {
		    var m = new Magi.Material(shader);
		    m.textures.DiffTex = m.textures.SpecTex = m.textures.EmitTex = null;
		    m.floats.MaterialSpecular = vec4.create([1, 1, 1, 0]);
		    m.floats.MaterialDiffuse = vec4.create([0.5, 0.5, 0.5, 1]);
		    m.floats.MaterialAmbient = vec4.create([1, 1, 1, 1]);
		    m.floats.MaterialShininess = 1.5;

		    m.floats.LightPos = vec4.create([7, 7, 7, 1.0]);
		    m.floats.GlobalAmbient = vec4.create([1, 1, 1, 1]);
		    m.floats.LightSpecular = vec4.create([0.8, 0.8, 0.95, 1]);
		    m.floats.LightDiffuse = vec4.create([0.7, 0.6, 0.9, 1]);
		    m.floats.LightAmbient = vec4.create([0.1, 0.10, 0.2, 1]);
		    m.floats.LightConstantAtt = 0.0;
		    m.floats.LightLinearAtt = 0.1;
		    m.floats.LightQuadraticAtt = 0.0;
		    return m;
		  }

		}

		        // the goal here is to make simple things simple
		        
		        // Reasonable defaults:
		        // - default shader [with multi-texturing (diffuse, specular, normal?)]
		        // - camera position
		        // - scene navigation controls
		        
		        // Simple things:
		        // - drawing things with lighting
		        // - making things move [like CSS transitions?]
		        // - text 
		        // - images
		        // - painter's algorithm for draw list sort
		        // - loading and displaying models
		        // - picking

		        // Easy fancy things:
		        // - rendering to FBOs (scene.renderTarget = fbo)
		        
		        /*
		        ren.scene.addFrameListener(function(t,dt) {
		          var l = Matrix.mulv4(ren.camera.getLookMatrix(), [7, 7, 7, 1.0]);
		          this.material.floats.LightPos = l
		        });
		        */

//OBJ_LOADER/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

		Magi.Obj = function(){};
		Magi.Obj.load = function(fileData) {
		  var o = new Magi.Obj();
		  o.parse(fileData);
		  return o;
		}
		Magi.Obj.prototype = {
		  load: function(fileData) {
		  
		          self.parse(xhr.responseText);
		       
		  },

		  onerror : function(xhr) {
		    alert("Error: "+xhr.status);
		  },

		  makeVBO : function(gl) {
		    if (this.texcoords) {
		      return new Magi.VBO(gl,
		          {size:3, data: this.vertices},
		          {size:3, data: this.normals},
		          {size:2, data: this.texcoords}
		      )
		    } else {
		      return new Magi.VBO(gl,
		          {size:3, data: this.vertices},
		          {size:3, data: this.normals}
		      )
		    }
		  },

		  cache : {},
		  getCachedVBO : function(gl) {
		    if (!this.cache[gl])
		      this.cache[gl] = this.makeVBO(gl);
		    return this.cache[gl];
		  },

		  parse : function(data) {
		    var t = new Date;
		    var geo_faces = [],
		        nor_faces = [],
		        tex_faces = [],
		        raw_vertices = [],
		        raw_normals = [],
		        raw_texcoords = [];
		    var lines = data.split("\n");
		    var hashChar = '#'.charCodeAt(0);
		    for (var i=0; i<lines.length; i++) {
		      var l = lines[i];
		      var vals = l.replace(/^\s+|\s+$/g, '').split(" ");
		      if (vals.length == 0) continue;
		      if (vals[0].charCodeAt(0) == hashChar) continue;
		      switch (vals[0]) {
		        case "g": // named object mesh [group]?
		          break;
		        case "v":
		          raw_vertices.push(parseFloat(vals[1]));
		          raw_vertices.push(parseFloat(vals[2]));
		          raw_vertices.push(parseFloat(vals[3]));
		          break;
		        case "vn":
		          raw_normals.push(parseFloat(vals[1]));
		          raw_normals.push(parseFloat(vals[2]));
		          raw_normals.push(parseFloat(vals[3]));
		          break;
		        case "vt":
		          raw_texcoords.push(parseFloat(vals[1]));
		          raw_texcoords.push(parseFloat(vals[2]));
		          break;
		        case "f":
		          // triangulate the face as triangle fan
		          var faces = [];
		          for (var j=1, v; j<vals.length; j++) {
		            if (j > 3) {
		              faces.push(faces[0]);
		              faces.push(v);
		            }
		            v = vals[j];
		            faces.push(v);
		          }
		          for (var j=0; j<faces.length; j++) {
		            var f = faces[j];
		            var a = f.split("/");
		            geo_faces.push(parseInt(a[0]) - 1);
		            if (a.length > 1)
		              tex_faces.push(parseInt(a[1]) - 1);
		            if (a.length > 2)
		              nor_faces.push(parseInt(a[2]) - 1);
		          }
		          break;
		      }
		    }
		    this.vertices = this.lookup_faces(raw_vertices, geo_faces, 3);
		    if (tex_faces.length > 0)
		      this.texcoords = this.lookup_faces(raw_texcoords, tex_faces, 2);
		    if (nor_faces.length > 0 && !this.overrideNormals)
		      this.normals = this.lookup_faces(raw_normals, nor_faces, 3);
		    else
		      this.normals = this.calculate_normals(this.vertices);
		    var bbox = {min:[0,0,0], max:[0,0,0]};
		    for (var i=0; i<raw_vertices.length; i+=3) {
		      var x = raw_vertices[i],
		          y = raw_vertices[i+1],
		          z = raw_vertices[i+2];
		      if (x < bbox.min[0]) bbox.min[0] = x;
		      else if (x > bbox.max[0]) bbox.max[0] = x;
		      if (y < bbox.min[1]) bbox.min[1] = y;
		      else if (y > bbox.max[1]) bbox.max[1] = y;
		      if (z < bbox.min[2]) bbox.min[2] = z;
		      else if (z > bbox.max[2]) bbox.max[2] = z;
		    }
		    bbox.width = bbox.max[0] - bbox.min[0];
		    bbox.height = bbox.max[1] - bbox.min[1];
		    bbox.depth = bbox.max[2] - bbox.min[2];
		    bbox.diameter = Math.max(bbox.width, bbox.height, bbox.depth);
		    this.boundingBox = bbox;
		    this.parseTime = new Date() - t;
		  },

		  lookup_faces : function(verts, faces, sz) {
		    var v = [];
		    for (var i=0; i<faces.length; i++) {
		      var offset = faces[i] * sz;
		      for (var j=0; j<sz; j++)
		        v.push(verts[offset+j]);
		    }
		    return v;
		  },

		  calculate_normals : function(verts) {
		    var norms = [];
		    for (var i=0; i<verts.length; i+=9) {
		      var normal = this.find_normal(
		        verts[i  ], verts[i+1], verts[i+2],
		        verts[i+3], verts[i+4], verts[i+5],
		        verts[i+6], verts[i+7], verts[i+8]);
		      for (var j=0; j<3; j++) {
		        norms.push(normal[0]);
		        norms.push(normal[1]);
		        norms.push(normal[2]);
		      }
		    }
		    return norms;
		  },

		  find_normal : function(x0,y0,z0, x1,y1,z1, x2,y2,z2) {
		    var u = [x0-x1, y0-y1, z0-z1];
		    var v = [x1-x2, y1-y2, z1-z2];
		    var w = [x2-x0, y2-y0, z2-z0];
		    var n = vec3.cross(u,v);
		    if (vec3.lengthSquare(n) == 0)
		      n = vec3.cross(v,w);
		    if (vec3.lengthSquare(n) == 0)
		      n = vec3.cross(w,u);
		    if (vec3.lengthSquare(n) == 0)
		      n = [0,0,1];
		    return vec3.normalize(n);
		  }

		}
		
//INITIALIZATION CODE/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    
  

    alert_webGL = function(string) {
        alert(string);
    }

 
    
    texture_webGL = function(t_num){
    
        var tex = new Texture();
        tex.image = new Image();
        if (t_num == 1)
        	tex.image.src = 'images/texture_metal.jpg';
        else if(t_num == 2)
        	tex.image.src = 'images/texture_wood.jpg';
        else if(t_num == 3)
        	tex.image.src = 'images/texture_grass.jpg';
        $('info').innerHTML = 'Loading texture (56kB)...';
        tex.image.onload = function(){
        	   $('info').innerHTML = '';
    		   g_n.material = DefaultMaterial.get();
               g_n.material.textures.DiffTex = tex;
               s.scene.appendChild(g_n);
        }
    }

    var g_s;   
    
    hide_webGL = function(){
    	//g_s.destroyStuff($('c'));
    	//Magi.AllocatedResources.hideAll();
    	//g_s.scene.destroy();
    	//delete g_s.scene;
    	//delete g_s.camera;
        //delete g_s;
    	//Magi.AllocatedResources.deleteAll();
    }
    
    init_webGL = function(fileData, pathImgs) {

    	 
            g_s = new Magi.Scene(document.getElementById('c'));
            
            $('info').innerHTML = 'Loading model...';
            var w = Magi.Obj.load(fileData);
              g_s.camera.position = [0, 2, 7];
              var tex = new Magi.Texture();
              tex.image = new Image();
              tex.image.src = pathImgs + 'texture_metal.jpg';
              $('info').innerHTML = 'Loading image texture...';
              
              tex.image.onload = function(){
                $('info').innerHTML = '';
                var n = new Magi.Node();
                var sc = 4.0 / (w.boundingBox.diameter);
                n.scaling = [sc, sc, sc];
                n.model = w.makeVBO();
                n.position[1] = 0.5;
                n.material = Magi.DefaultMaterial.get();
                n.material.floats.MaterialDiffuse = [.2,1,.2,0];
                n.material.textures.DiffTex = tex;
    			g_s.useDefaultCameraControls();
    			
    			
                g_s.scene.appendChild(n);
                
              }
      }
    
    saveImgWebGL = function(){
    	   return g_s.getCanvasData($('c'));

    }
	  