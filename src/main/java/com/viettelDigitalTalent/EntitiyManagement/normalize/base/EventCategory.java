package com.viettelDigitalTalent.EntitiyManagement.normalize.base;

public enum EventCategory {
    AUTHENTICATION, // Đăng nhập, đăng xuất, đổi mật khẩu
    PROCESS,        // Chạy tiến trình, thực thi lệnh
    NETWORK,        // Kết nối, socket, tường lửa
    CLOUD,          // API Cloud, IAM, storage access
    FILE_SYSTEM,    // Đọc/ghi/xóa file
    THREAT          // Cảnh báo từ các thiết bị bảo mật (IDS/IPS, Antivirus)
}
