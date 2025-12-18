db = db.getSiblingDB('qubitlock');

db.createUser({
  user: 'qubitlock_user',
  pwd: 'password',
  roles: [
    { role: 'readWrite', db: 'qubitlock' },
    { role: 'dbAdmin', db: 'qubitlock' }
  ]
});

db.createCollection('metadata');
db.createCollection('fs.files');
db.createCollection('fs.chunks');

// Для проверки
db.metadata.insertOne({
  test: 'connection',
  timestamp: new Date(),
  message: 'MongoDB initialized successfully!'
});

print('✅ MongoDB успешно инициализирована!');
print('   База данных: qubitlock');
print('   User: qubitlock_user / password');
print('   Коллекции: metadata, fs.files, fs.chunks');
