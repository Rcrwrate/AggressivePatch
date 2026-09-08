git clone --depth=1 https://cnb.cool/shirokasoke/GTNH /tmp/GTNH
cd /tmp/GTNH
git lfs pull

mv /tmp/GTNH/2.9.0-beta2 /workspace/GTNH-server

wget https://cnb.cool/shirokasoke/McWebAPI/-/releases/download/2.8.4-0.4-pre/2.9.0-beta2.dumps.7z
7z x 2.9.0-beta2.dumps.7z -o/workspace/GTNH-server

rm -rf /tmp/GTNH

echo '/usr/lib/jvm/zulu25-ca-amd64/bin/java -Xms6G -Xmx32G -Dmixin.debug.export=true -Dfml.readTimeout=180 @java9args.txt -jar lwjgl3ify-forgePatches.jar nogui' > /workspace/GTNH-server/startserver-java9.sh
# cat ./GTNH-server/logs/fml-junk-earlystartup.log | grep -oE '[0-9]+ms' | sort | uniq -c | sort -rn