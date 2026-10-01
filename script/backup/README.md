# FunFriday database backup

`funfriday-backup.sh` creates a compressed `GameData` MySQL dump and uploads it
to `FunFriday:FunFridayBackups/gamedata-latest.sql.gz`. The fixed remote name
means each successful daily backup replaces the prior one.

Before the first deployment, create the credentials file on the server:

```bash
sudo install -d -m 700 /etc/funfriday
sudo cp script/backup/mysql-backup.cnf.example /etc/funfriday/mysql-backup.cnf
sudo chmod 600 /etc/funfriday/mysql-backup.cnf
sudo nano /etc/funfriday/mysql-backup.cnf
```

Set the real MySQL username and password in that file. Confirm that `rclone
listremotes` shows `FunFriday:` for the same server user that runs the backup.

After deployment, test it with:

```bash
sudo /usr/local/sbin/funfriday-backup
rclone ls FunFriday:FunFridayBackups
```
