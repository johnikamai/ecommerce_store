FROM mysql:8.0
COPY deploy/mysql/10-owned-schemas.sh /docker-entrypoint-initdb.d/10-owned-schemas.sh
RUN chmod 0555 /docker-entrypoint-initdb.d/10-owned-schemas.sh
EXPOSE 3306
