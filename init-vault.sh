#!/bin/sh

echo "Запуск инициализации HashiCorp Vault..."

vault server -dev -dev-root-token-id=root-token -dev-listen-address=0.0.0.0:8200 &
VAULT_PID=$!

echo "Ожидание запуска Vault..."
sleep 3

export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='root-token'

echo "Проверка доступности Vault..."
until vault status > /dev/null 2>&1; do
    echo "   Ожидание Vault..."
    sleep 1
done

echo "✅ Vault запущен и доступен"

echo "Включение движка Transit..."
if ! vault secrets list | grep -q "transit/"; then
    vault secrets enable transit
    echo "   ✅ Transit движок включен"
else
    echo "   ℹ️ Transit движок уже включен"
fi

echo "Создание ключа 'qubitlock'..."
if ! vault read transit/keys/qubitlock > /dev/null 2>&1; then
    vault write -f transit/keys/qubitlock type="aes256-gcm96"
    echo "   ✅ Ключ 'qubitlock' создан"
else
    echo "   ℹ️ Ключ 'qubitlock' уже существует"
fi

echo ""
echo "   Инициализация Vault завершена!"
echo "   URL: http://localhost:8200"
echo "   Token: root-token"
echo "   Transit key: qubitlock"
echo ""

echo "Проверка работоспособности..."
TEST_DATA=$(echo -n "Hello from init script" | base64)
RESULT=$(vault write -format=json transit/encrypt/qubitlock plaintext="$TEST_DATA" 2>/dev/null || true)

if [ -n "$RESULT" ]; then
    echo "✅ Шифрование работает корректно"
else
    echo "❌ Ошибка проверки шифрования"
fi

echo ""
echo "Vault готов к работе!"
echo ""

wait $VAULT_PID