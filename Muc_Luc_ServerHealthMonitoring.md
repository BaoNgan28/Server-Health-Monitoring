# MỤC LỤC

# CHƯƠNG 1. Giới thiệu về dự án Server Health Monitoring System

## 1.1. Tổng quan về dự án
- Tên dự án: Remote Server Health Monitoring System
- Lớp: 23DTHA5 - Nhóm 10
- Môn học: Computer Network Programming
- Mô tả: Hệ thống giám sát sức khỏe server dựa trên Java, sử dụng hai giao thức UDP (telemetry) và TCP (control)

## 1.2. Thành viên nhóm
- MSSV 2380601424: Nguyễn Thành Bảo Ngô (Repository initialization, Agent foundation)
- MSSV 2380601465: Lê Hương Ngọc (Shared protocol, HMAC signing, Monitor TCP/UDP)
- MSSV 2182310415: Bài Tiến Quốc (SQLite persistence, alert aggregation)
- MSSV 2380613311: Nguyễn Phước Nam (Monitor configuration, Agent management)
- MSSV 2380601581: Trình Thùy Yến Nhì (Visualization, UI components)

## 1.3. Mục tiêu nghiên cứu
- Xây dựng hệ thống giám sát sức khỏe server trong mạng lân cận
- Sử dụng hai giao thức truyền thông: UDP cho telemetry, TCP cho control
- Triển khai cơ chế bảo mật HMAC và replay protection
- Cung cấp giao diện Swing dashboard để theo dõi metrics theo thời gian thực

## 1.4. Cấu trúc đề tài
- Tổng quan về bố cục các chương trong báo cáo

# CHƯƠNG 2. KIẾN TRÚC HỆ THỐNG VÀ MÔ HÌNH KỸ THUẬT

## 2.1. Tổng quan về kiến trúc hệ thống
- Kiến trúc Client-Server hai lớp: Agent (client) và Monitoring Client (server)
- Giao thức truyền thông: UDP cho telemetry/heartbeats, TCP cho Agent-control/configuration
- Mô hình bảo mật: HMAC signing và replay protection

## 2.2. Thiết kế hệ thống Backend
- Cấu trúc module: monitoring-common, agent-app, monitor-app, simulator-app
- Chịnh tuyến xử lý: Message models, protocol definition, security utilities

## 2.3. Thiết kế giao diện người dùng (Frontend)
- Giao diện Swing GUI cho Monitoring Client
- Cấu trúc màn hình: MainFrame, dashboard, agent management
- Biểu đồ hiển thị: CPU, RAM, Network IN/OUT, charts (120 points sliding window)

## 2.4. Cấu hình và triển khai
- Cấu hình demo: 4 Agents (AGENT-01 đến AGENT-04), TCP ports 5001-5004
- UDP port: 6000 trên 127.0.0.1
- Cấu hình shared secret: GROUP10_DEMO_SECRET_01 đến _04

# CHƯƠNG 3. TRIỂN KHAI CÀI ĐẶT VÀ CHẠY THỬ NGHIỆM

## 3.1. Yêu cầu hệ thống
- JDK 17
- Apache Maven
- Windows cho các script .bat launch
- Cổng UDP cho Monitoring Client, cổng TCP riêng cho mỗi Agent

## 3.2. Các bước build và test
- Lệnh Maven: `mvn -B clean test` và `mvn -B clean package`
- Scripts hỗ trợ: `scripts/test.bat`, `scripts\build.bat`

## 3.3. Local monitoring demo
- Chạy Monitoring Client: `scripts\run-monitor.bat`
- Chạy 4 Agents: `scripts\run-agent01.bat`, `run-agent02.bat`, `run-agent03.bat`, `run-agent04.bat`
- Hoặc dùng script tập thể: `scripts\run-local-demo.bat`

## 3.4. Demo script sequence (19 steps)
- Bước 1: Architecture overview (ARCHITECTURE.md)
- Bước 2: Start monitoring client
- Bước 3: Start four agents (run-local-demo.bat)
- Bước 4-20: Các bước test các chức năng (dashboard, CPU/memory, network traffic, charts, TCP configuration, CPU alert, email notification, offline detection, restart agent, forged packets, replay test, history, aggregation)

# CHƯƠNG 4. TRIỂN KHAI CÀI ĐẶT PHÉN BÌNH VÀ TRIỂN KHAI ĐA CƠ CẢNH

## 4.1. Cài đặt độc lập (Single machine)
- Cấu hình cho môi trường test trên một máy tính
- Cấu hình port và agent IDs
- Kiểm tra firewall requirements

## 4.2. Cài đặt đa máy (Multi-machine)
- Cấu hình firewall cho nhiều máy
- Cấu hình network topology
- Cáchdeploy agents trên các máy khác nhau

## 4.3. Troubleshooting common issues
- Các vấn đề thường gặp khi cài đặt và chạy hệ thống
- Hướng dẫn giải quyết các lỗi phổ biến

# CHƯƠNG 5. KẾT LUẬN VÀ KIẾN NGHỊ

## 5.1. Kết luận
- Tổng kết các thành quả đã đạt được
- Đánh giá hiệu suất hệ thống
- Phản biện về kiến trúc sử dụng hai giao thức UDP/TCP

## 5.2. Hạn chế
- Các khái niệm và kỹ thuật cần cải thiện
- Yêu cầu phần cứng và môi trường

## 5.3. Hướng phát triển trong tương lai
- Tích hợp thêm tính năng mới
- Mở rộng khả năng giám sát
- Tối ưu hóa hiệu suất

# TÀI LIỆU THAM KHẢO

## **I. Tài liệu chính dự án**

- [1] README.md - Hướng dẫn dự án đầy nhất
- [2] docs/ARCHITECTURE.md - Kiến trúc hệ thống
- [3] docs/DEMO_SCRIPT.md - Kịch bản demo
- [4] docs/NETBEANS_SETUP.md - Cài đặt NetBeans
- [5] docs/TEST_REPORT.md - Báo cáo kiểm tra
- [6] docs/TROUBLESHOOTING.md - Khắc phục sự cố

## **II. Tài liệu tham khảo kỹ thuật**

- [7] Oracle JDK 17 Documentation
- [8] Apache Maven Guide
- [9] Oracle Swing GUI Documentation
- [10] Underthesea Vietnamese NLP Documentation

# PHỤ LỤC

## **Hình minh họa**

- **Hình 1.1.** Cấu trúc tổng thể hệ thống Client-Server hai lớp
  *Mô tả: Biểu đồ khối thể hiện mối quan hệ giữa Monitoring Client và các Agent*

- **Hình 1.2.** Mô hình giao thức UDP/TCP trong hệ thống
  *Mô tả: Biểu đồ minh họa hai giao thức truyền thông: UDP cho telemetry, TCP cho control*

- **Hình 2.1.** Cấu trúc module dự án Java
  *Mô tả: Biểu đồ thể hiện 4 module chính: monitoring-common, agent-app, monitor-app, simulator-app*

- **Hình 3.1.** Biểu đồ luồng cài đặt local demo
  *Mô tả: Bước-step luồng cài đặt và chạy demo trên một máy tính*

- **Hình 4.1.** Biểu đồ cấu hình multi-machine deployment
  *Mô tả: Mạng lưới các máy tính kết nối cho việcdeploy đa mục*

- **Hình 5.1.** Lộ trình phát triển tương lai
  *Mô tả: Các tính năng có thể thêm vào các phiên bản sau*

---

*File mục lục này được tạo dựa trên cấu trúc thực tế của dự án ServerHealthMonitoring, bao gồm 5 chương chính và các tài liệu hỗ trợ trong repository.*