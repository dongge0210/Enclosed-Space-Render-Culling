package com.dongge0210.enclosedculling.client.gpu;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 视锥剔除系统 - 剔除视野范围外的对象
 * GPU优化: 减少需要渲染的几何体数量
 */
public class FrustumCuller {
    
    private static final float[] frustumPlanes = new float[24]; // 6个平面，每个4个系数
    private static boolean frustumValid = false;
    
    /**
     * 更新视锥体平面
     */
    public static void updateFrustum(Matrix4f projectionMatrix, Matrix4f modelViewMatrix) {
        Matrix4f combinedMatrix = new Matrix4f(projectionMatrix);
        combinedMatrix.mul(modelViewMatrix);
        
        // 提取6个视锥体平面 (左、右、下、上、近、远)
        extractFrustumPlanes(combinedMatrix);
        frustumValid = true;
    }
    
    /**
     * 从组合矩阵中提取视锥体平面
     */
    private static void extractFrustumPlanes(Matrix4f matrix) {
        float[] m = new float[16];
        matrix.get(m);
        
        // 左平面
        frustumPlanes[0] = m[3] + m[0];
        frustumPlanes[1] = m[7] + m[4];
        frustumPlanes[2] = m[11] + m[8];
        frustumPlanes[3] = m[15] + m[12];
        
        // 右平面
        frustumPlanes[4] = m[3] - m[0];
        frustumPlanes[5] = m[7] - m[4];
        frustumPlanes[6] = m[11] - m[8];
        frustumPlanes[7] = m[15] - m[12];
        
        // 下平面
        frustumPlanes[8] = m[3] + m[1];
        frustumPlanes[9] = m[7] + m[5];
        frustumPlanes[10] = m[11] + m[9];
        frustumPlanes[11] = m[15] + m[13];
        
        // 上平面
        frustumPlanes[12] = m[3] - m[1];
        frustumPlanes[13] = m[7] - m[5];
        frustumPlanes[14] = m[11] - m[9];
        frustumPlanes[15] = m[15] - m[13];
        
        // 近平面
        frustumPlanes[16] = m[3] + m[2];
        frustumPlanes[17] = m[7] + m[6];
        frustumPlanes[18] = m[11] + m[10];
        frustumPlanes[19] = m[15] + m[14];
        
        // 远平面
        frustumPlanes[20] = m[3] - m[2];
        frustumPlanes[21] = m[7] - m[6];
        frustumPlanes[22] = m[11] - m[10];
        frustumPlanes[23] = m[15] - m[14];
        
        // 标准化平面方程
        for (int i = 0; i < 6; i++) {
            int offset = i * 4;
            float length = (float) Math.sqrt(
                frustumPlanes[offset] * frustumPlanes[offset] +
                frustumPlanes[offset + 1] * frustumPlanes[offset + 1] +
                frustumPlanes[offset + 2] * frustumPlanes[offset + 2]
            );
            if (length > 0) {
                frustumPlanes[offset] /= length;
                frustumPlanes[offset + 1] /= length;
                frustumPlanes[offset + 2] /= length;
                frustumPlanes[offset + 3] /= length;
            }
        }
    }
    
    /**
     * 检查点是否在视锥体内
     */
    public static boolean isPointInFrustum(Vec3 point) {
        if (!frustumValid) return true; // 如果视锥体未更新，保守地渲染
        
        return isPointInFrustum((float) point.x, (float) point.y, (float) point.z);
    }
    
    /**
     * 检查点是否在视锥体内
     */
    public static boolean isPointInFrustum(float x, float y, float z) {
        if (!frustumValid) return true;
        
        for (int i = 0; i < 6; i++) {
            int offset = i * 4;
            float distance = frustumPlanes[offset] * x + 
                           frustumPlanes[offset + 1] * y + 
                           frustumPlanes[offset + 2] * z + 
                           frustumPlanes[offset + 3];
            if (distance < 0) {
                return false; // 点在平面外侧
            }
        }
        return true;
    }
    
    /**
     * 检查球体是否与视锥体相交
     */
    public static boolean isSphereInFrustum(Vec3 center, float radius) {
        if (!frustumValid) return true;
        
        return isSphereInFrustum((float) center.x, (float) center.y, (float) center.z, radius);
    }
    
    /**
     * 检查球体是否与视锥体相交
     */
    public static boolean isSphereInFrustum(float x, float y, float z, float radius) {
        if (!frustumValid) return true;
        
        for (int i = 0; i < 6; i++) {
            int offset = i * 4;
            float distance = frustumPlanes[offset] * x + 
                           frustumPlanes[offset + 1] * y + 
                           frustumPlanes[offset + 2] * z + 
                           frustumPlanes[offset + 3];
            if (distance < -radius) {
                return false; // 球体完全在平面外侧
            }
        }
        return true;
    }
    
    /**
     * 检查AABB是否与视锥体相交
     */
    public static boolean isAABBInFrustum(Vec3 min, Vec3 max) {
        if (!frustumValid) return true;
        
        return isAABBInFrustum(
            (float) min.x, (float) min.y, (float) min.z,
            (float) max.x, (float) max.y, (float) max.z
        );
    }
    
    /**
     * 检查AABB是否与视锥体相交
     */
    public static boolean isAABBInFrustum(float minX, float minY, float minZ, 
                                         float maxX, float maxY, float maxZ) {
        if (!frustumValid) return true;
        
        for (int i = 0; i < 6; i++) {
            int offset = i * 4;
            float a = frustumPlanes[offset];
            float b = frustumPlanes[offset + 1];
            float c = frustumPlanes[offset + 2];
            float d = frustumPlanes[offset + 3];
            
            // 找到AABB在平面法向量方向上的最远点
            float px = a > 0 ? maxX : minX;
            float py = b > 0 ? maxY : minY;
            float pz = c > 0 ? maxZ : minZ;
            
            if (a * px + b * py + c * pz + d < 0) {
                return false; // AABB完全在平面外侧
            }
        }
        return true;
    }
    
    /**
     * 检查方块位置是否在视锥体内
     */
    public static boolean isBlockInFrustum(BlockPos pos) {
        return isAABBInFrustum(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1
        );
    }
    
    /**
     * 自动更新视锥体（从当前相机）
     */
    public static void autoUpdateFrustum() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && mc.level != null) {
            try {
                // 这里需要访问渲染系统的矩阵，可能需要通过事件或mixin
                // 暂时标记为需要在渲染事件中调用
                frustumValid = false;
            } catch (Exception e) {
                frustumValid = false;
            }
        }
    }
    
    /**
     * 重置视锥体状态
     */
    public static void reset() {
        frustumValid = false;
    }
    
    /**
     * 获取视锥体状态信息
     */
    public static String getFrustumInfo() {
        return "Frustum Culling: " + (frustumValid ? "ACTIVE" : "INACTIVE");
    }
}
