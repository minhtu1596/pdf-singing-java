#!/bin/bash
cd /home/ubuntu/pdf-singing-java

echo "=== 1. Kéo code mới nhất ==="
git pull origin version5

echo "=== 2. Biên dịch và đóng gói bản mới ==="
# Nếu build thất bại, script sẽ DỪNG LẠI NGAY TẠI ĐÂY, không làm ảnh hưởng đến app cũ đang chạy
./mvnw clean package -DskipTests || { echo "Build thất bại rồi sếp ơi!"; exit 1; }

echo "=== 3. Chỉ khi build thành công, mới tắt app cũ và đi vào target ==="
cd target
pkill -f 'signingservice-0.0.1-SNAPSHOT.jar' || true

echo "=== 4. Khởi động app mới ==="
nohup java -jar signingservice-0.0.1-SNAPSHOT.jar > app.log 2>&1 &

echo "=== DEPLOY THÀNH CÔNG ==="
