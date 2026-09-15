package com.xiaofan.fanVerify.utils;

import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;

public class QRCodeMapRenderer extends MapRenderer {
    
    private final BufferedImage qrCode;
    private boolean rendered = false;
    
    public QRCodeMapRenderer(BufferedImage qrCode) {
        this.qrCode = qrCode;
    }
    
    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        if (rendered) return;
        
        // 将二维码缩放到 128x128 (Minecraft 地图尺寸)
        BufferedImage scaled = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        scaled.getGraphics().drawImage(qrCode, 0, 0, 128, 128, null);
        
        // 绘制到地图上
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                int rgb = scaled.getRGB(x, y);
                // 转换为 Minecraft 地图颜色
                byte color = getMapColor(rgb);
                canvas.setPixel(x, y, color);
            }
        }
        
        rendered = true;
    }
    
    private byte getMapColor(int rgb) {
        // 简单的灰度转换
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int gray = (r + g + b) / 3;
        
        // Minecraft 地图颜色范围: 0-255
        // 黑色到白色的灰度映射
        if (gray < 85) return 119;      // 黑色
        else if (gray < 170) return 8;  // 灰色
        else return 34;                  // 白色
    }
}
