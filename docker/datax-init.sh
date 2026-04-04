#!/bin/bash
# DataX 初始化：首次启动时下载安装
DATAX_HOME="/opt/datax"

if [ ! -f "$DATAX_HOME/bin/datax.py" ]; then
  echo "Downloading DataX..."
  cd /tmp
  curl -sSL -o datax.tar.gz "https://datax-opensource.oss-cn-hangzhou.aliyuncs.com/202309/datax.tar.gz"
  tar -xzf datax.tar.gz -C /opt/
  rm -f datax.tar.gz
  echo "DataX installed at $DATAX_HOME"
else
  echo "DataX already installed."
fi
