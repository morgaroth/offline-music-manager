# Syncing music to Navidrome via NFS

Assuming your NFS export is at `SERVER:/media/nfs/navidrome`, you need to set up an automount so the library syncs seamlessly without manual mounting.

## 1. Create the mount point

```bash
sudo mkdir -p /mnt/nfss/navidrome
```

## 2. Create the systemd mount unit

The unit filename **must** match the mount path (`/mnt/nfss/navidrome` → `mnt-nfss-navidrome`).

```bash
sudo tee /etc/systemd/system/mnt-nfss-navidrome.mount << 'EOF'
[Unit]
Description=Navidrome NFS music library

[Mount]
What=SERVER:/media/nfs/navidrome
Where=/mnt/nfss/navidrome
Type=nfs4
Options=rw,soft,timeo=50

[Install]
WantedBy=multi-user.target
EOF
```

Replace `SERVER` with the IP or hostname of your NFS server (e.g. `192.168.0.20`).

## 3. Create the automount unit

This makes the mount happen on first access and unmount after 60s idle.

```bash
sudo tee /etc/systemd/system/mnt-nfss-navidrome.automount << 'EOF'
[Unit]
Description=Automount Navidrome NFS library

[Automount]
Where=/mnt/nfss/navidrome
TimeoutIdleSec=60

[Install]
WantedBy=multi-user.target
EOF
```

## 4. Enable and start

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now mnt-nfss-navidrome.automount
```

## 5. Verify

```bash
ls /mnt/nfss/navidrome
```

This should trigger the mount and show the remote files.

## 6. Sync your music

```bash
rsync -avz ~/music-library/all-music/ /mnt/nfss/navidrome/
```

Or set the app's destination directory directly to `/mnt/nfss/navidrome/` — the automount handles the rest transparently.

## Notes

- Systemd mount unit filenames must exactly correspond to the mount path (slashes become dashes, leading slash dropped). Getting this wrong results in `Where= setting doesn't match unit name. Refusing.`
- Navidrome will pick up new files on its next periodic scan, or you can trigger a scan from the Navidrome UI.
- Make sure NFS port 2049 is reachable from your machine and the export allows your IP.
