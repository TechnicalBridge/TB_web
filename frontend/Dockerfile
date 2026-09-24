# =============================================================================
#  El portal: se compila con Node y se sirve con nginx.
# =============================================================================
#  En desarrollo Vite sirve el codigo y recarga al guardar. Eso no sirve para
#  desplegar: aqui se compila una vez a HTML, CSS y JavaScript, y los sirve
#  nginx, que para eso esta hecho. La imagen final no lleva Node.
# =============================================================================

FROM node:22-alpine AS construccion
WORKDIR /obra

#  package.json y el candado primero: mientras no cambien, Docker se salta
#  la instalacion entera.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build


#  La imagen oficial de nginx arranca su proceso maestro como root. Esta es la
#  misma nginx preparada para no hacerlo: corre como el usuario 101 y escucha
#  en el 8080, porque un proceso sin privilegios no puede tomar el puerto 80.
FROM nginxinc/nginx-unprivileged:alpine
COPY --from=construccion /obra/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 8080
