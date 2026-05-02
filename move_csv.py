import time
import os
import shutil
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler

# --- 設定 ---
WATCH_DIR = os.path.expanduser("~/Downloads")
TARGET_DIR = "/Users/fujinoikki/Desktop/java_app/TraningApp/input/"
# -----------

class MoveHandler(FileSystemEventHandler):
    def on_created(self, event):
        if event.is_directory:
            return
        
        filename = os.path.basename(event.src_path)
        
        # 「master_data」という文字が含まれているCSVなら何でも対象にする
        if "master_data" in filename and filename.endswith(".csv"):
            # 【重要】ブラウザの書き込み完了を待つ（時間を少し延ばしました）
            time.sleep(2)
            
            dest_path = os.path.join(TARGET_DIR, "master_data.csv")
            
            # リトライ処理：ファイルが見つかるまで最大5回試す
            for i in range(5):
                if os.path.exists(event.src_path):
                    try:
                        if os.path.exists(dest_path):
                            os.remove(dest_path)
                        shutil.move(event.src_path, dest_path)
                        print(f"🚀 {filename} を master_data.csv として移動完了！")
                        return # 成功したら終了
                    except Exception as e:
                        print(f"⚠️ リトライ中... ({i+1}/5): {e}")
                
                time.sleep(1) # 見つからなければ1秒待機
            
            print(f"❌ 失敗: ファイルが見つかりませんでした: {event.src_path}")

# 実行部分は前回と同じ
observer = Observer()
handler = MoveHandler()
observer.schedule(handler, WATCH_DIR, recursive=False)
observer.start()

print(f"🔭 {WATCH_DIR} の監視を開始しました...")

try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    observer.stop()
    print("\n停止しました")
observer.join()
