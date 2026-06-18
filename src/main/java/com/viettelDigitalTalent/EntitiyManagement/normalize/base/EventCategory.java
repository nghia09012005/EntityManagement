package com.viettelDigitalTalent.EntitiyManagement.normalize.base;

public enum EventCategory {
    AUTHENTICATION, // Đăng nhập, đăng xuất, đổi mật khẩu
    PROCESS,        // Chạy tiến trình, thực thi lệnh
    NETWORK,        // Kết nối, socket, tường lửa
    THREAT          // Cảnh báo từ các thiết bị bảo mật (IDS/IPS, Antivirus)
}
