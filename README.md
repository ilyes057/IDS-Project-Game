# 🖧 Test distribué sur 2 PC

Ce guide explique **exactement** comment lancer le projet sur **2 PC différents** connectés au même réseau.

---

## Répartition des rôles

| Machine | Rôle |
|---------|------|
| **PC1** | Héberge RabbitMQ · Lance les workers **A** et **C** |
| **PC2** | Lance les workers **B** et **D** · Lance le client graphique |

Les **4 zones** sont ainsi actives simultanément.

---

## 1. Pré-requis

Sur **PC1 et PC2** :

- Java installé
- Maven installé
- Le projet cloné
- Les deux PC sur le **même réseau local**

Vérifier que le projet compile :

```bash
mvn clean compile
```

---

## 2. Configuration de RabbitMQ (sur PC1)

### 2.1 Lancer RabbitMQ dans Ubuntu / WSL

```bash
sudo systemctl start rabbitmq-server
sudo systemctl enable rabbitmq-server
sudo systemctl status rabbitmq-server
sudo ss -ltnp | grep 5672
```

### 2.2 Créer un utilisateur RabbitMQ

```bash
sudo rabbitmqctl add_user ids ids123
sudo rabbitmqctl set_permissions -p / ids ".*" ".*" ".*"
sudo rabbitmqctl set_user_tags ids administrator
```

### 2.3 Trouver l'IP Windows de PC1

```bash
ipconfig
# Exemple : 172.20.10.11
```

### 2.4 Trouver l'IP WSL de PC1

```bash
ip a
# Exemple : 172.17.194.7
```

### 2.5 Exposer RabbitMQ de WSL vers Windows

> Remplacer `<WSL_IP_PC1>` par l'IP trouvée à l'étape 2.4.

```powershell
netsh interface portproxy add v4tov4 `
  listenport=5672 listenaddress=0.0.0.0 `
  connectport=5672 connectaddress=<WSL_IP_PC1>

# Exemple :
netsh interface portproxy add v4tov4 `
  listenport=5672 listenaddress=0.0.0.0 `
  connectport=5672 connectaddress=172.17.194.7
```

### 2.6 Ouvrir le port 5672 dans le pare-feu Windows

```powershell
New-NetFirewallRule -DisplayName "RabbitMQ 5672" `
  -Direction Inbound -Protocol TCP -LocalPort 5672 -Action Allow
```

---

## 3. Vérifier la connectivité depuis PC2

> Remplacer `<IP_PC1>` par l'IP Windows de PC1 (étape 2.3).

```bash
nc -zv <IP_PC1> 5672

# Exemple :
nc -zv 172.20.10.11 5672
```

Un message `Connection succeeded` confirme que PC2 voit bien RabbitMQ sur PC1.

---

## 4. Lancer les 4 workers

> Dans toutes les commandes ci-dessous, remplacer `<IP_PC1>` par l'IP Windows de PC1.

### 4.1 Sur PC1 — Worker A

```bash
mvn -q exec:java \
  -Drabbitmq.host=<IP_PC1> \
  -Drabbitmq.user=ids \
  -Drabbitmq.password=ids123 \
  -Dexec.mainClass=com.game.Main \
  -Dexec.args=A
```

### 4.2 Sur PC1 — Worker C

*(Dans un deuxième terminal)*

```bash
mvn -q exec:java \
  -Drabbitmq.host=<IP_PC1> \
  -Drabbitmq.user=ids \
  -Drabbitmq.password=ids123 \
  -Dexec.mainClass=com.game.Main \
  -Dexec.args=C
```

### 4.3 Sur PC2 — Worker B

```bash
mvn -q exec:java \
  -Drabbitmq.host=<IP_PC1> \
  -Drabbitmq.user=ids \
  -Drabbitmq.password=ids123 \
  -Dexec.mainClass=com.game.Main \
  -Dexec.args=B
```

### 4.4 Sur PC2 — Worker D

*(Dans un deuxième terminal)*

```bash
mvn -q exec:java \
  -Drabbitmq.host=<IP_PC1> \
  -Drabbitmq.user=ids \
  -Drabbitmq.password=ids123 \
  -Dexec.mainClass=com.game.Main \
  -Dexec.args=D
```

---

## 5. Lancer le client graphique (sur PC2)

*(Dans un troisième terminal)*

```bash
mvn -q exec:java \
  -Drabbitmq.host=<IP_PC1> \
  -Drabbitmq.user=ids \
  -Drabbitmq.password=ids123 \
  -Dexec.mainClass=com.game.ui.GameViewerMain \
  -Dexec.args="A P1"

# Exemple avec IP réelle :
mvn -q exec:java \
  -Drabbitmq.host=172.20.10.11 \
  -Drabbitmq.user=ids \
  -Drabbitmq.password=ids123 \
  -Dexec.mainClass=com.game.ui.GameViewerMain \
  -Dexec.args="A P1"
```

---

## Récapitulatif des terminaux

| Terminal | Machine | Commande |
|----------|---------|----------|
| 1 | PC1 | Worker A |
| 2 | PC1 | Worker C |
| 1 | PC2 | Worker B |
| 2 | PC2 | Worker D |
| 3 | PC2 | Client graphique |
