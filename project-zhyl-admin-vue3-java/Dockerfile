FROM nginx
LABEL maintainer="husiyu"
 
COPY ./dist/  /usr/share/nginx/html/
RUN chmod -R 777 /usr/share/nginx/html/

RUN chmod -R 777 /etc/nginx/conf.d/default.conf
COPY ./default.conf  /etc/nginx/conf.d/

CMD ["nginx", "-g", "daemon off;"]