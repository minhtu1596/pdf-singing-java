#!/bin/bash

# 1. Di chuyển vào thư mục GỐC chứa dự án của bạn trên AWS
cd /home/ubuntu/pdf-singing-java

echo "=== 2. Backup file JAR cũ ==="
mkdir -p /home/ubuntu/pdf-singing-java/backup/
cp target/signingservice-0.0.1-SNAPSHOT.jar /home/ubuntu/pdf-singing-java/backup/app_backup.jar || echo "Chưa có file jar cũ, bỏ qua backup."

echo "=== 3. Kéo code mới nhất từ GitHub ==="
git pull origin main

echo "=== 4. Biên dịch và đóng gói dự án Java ==="
# Sử dụng trình đóng gói Maven của dự án để build ra file JAR mới
# Lệnh này sẽ tự động sinh ra file signingservice-0.0.1-SNAPSHOT.jar mới trong thư mục target/
./mvnw clean package -DskipTests

echo "=== 5. Restart ứng dụng Java ==="
# Di chuyển vào thư mục target chứa file JAR như câu lệnh chạy tay của bạn
cd /home/ubuntu/pdf-singing-java/target

# Tìm và tắt tiến trình Java cũ đang chạy file jar này (nếu có)
pkill -f 'signingservice-0.0.1-SNAPSHOT.jar' || true

# Chạy file .jar mới dưới nền (dùng nohup) để khi GitHub thoát SSH, app của bạn VẪN CHẠY
echo "Starting signingservice application..."
nohup java -jar signingservice-0.0.1-SNAPSHOT.jar > app.log 2>&1 &

echo "=== DONE! Deploy tự động thành công ==="
