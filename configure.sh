if [ "$1" = "local" ];
then
	HOSTNAME=$(hostname)
	mkdir -p config
	echo "$HOSTNAME" > config/slaves
else
head -n $1 ~hadoop/hadoop-2.6.0/etc/hadoop/slaves > config/slaves
fi

echo "Configured slaves are : "
cat config/slaves
