# Makefile for running Redis and MongoDB with Docker

# Run Redis CLI
run-redis-cli:
	docker run -it --rm redis redis-cli -h host.docker.internal -p 6379

# Run MongoDB
run-mongo-db:
	docker run -d --name mongodb -p 27017:27017 -e MONGO_INITDB_ROOT_USERNAME=admin -e MONGO_INITDB_ROOT_PASSWORD=secret mongo

# Stop MongoDB container
stop-mongo-db:
	docker stop mongodb && docker rm mongodb

start-mongo-compass:
	docker run -d --name mongo-express -p 8081:8081 --link mongodb:mongo \
      -e ME_CONFIG_MONGODB_ADMINUSERNAME=admin \
      -e ME_CONFIG_MONGODB_ADMINPASSWORD=secret \
      -e ME_CONFIG_MONGODB_URL="mongodb://admin:secret@mongodb:27017/" \
      mongo-express

stop-mongo-compass:
	docker stop mongo-express && docker rm mongo-express

# Stop Redis container
stop-redis:
	docker stop redis-server && docker rm redis-server

# Run Redis Server
run-redis-server:
	docker run -d --name redis-server -p 6379:6379 -e REDIS_PASSWORD=ichu redis redis-server --requirepass "ichu"

# Restart Redis Server
restart-redis:
	make stop-redis && make run-redis-server

# Restart MongoDB
restart-mongo:
	make stop-mongo-db && make run-mongo-db

# Show running containers
ps:
	docker ps

# Clean up all stopped containers
clean:
	docker system prune -f

admin-setup:
	INSERT INTO public.yt_user ( \
         id, account_status, email_id, is_email_verified, is_user_approved, \
         mobile_number, password, user_role, username, full_name, created_at \
     ) \
     VALUES ( \
         1, 'ACTIVE', 'abhijith.anjana@gmail.com', true, true,  \
         '08848331138', '$2a$10$WxZEOlK0PcfYXEuNFfnRZ.0Lz.gZBiIfAz8Zv.Sl6FEmDHO1EcJzO', \
         'ADMIN', 'admin', 'Admin', NOW() \
     );&&\
     INSERT INTO public.yt_channel(channel_name, created_at, youtube_channel_id, admin_id) values('test_channel', now(), 'abhianil', 1)
