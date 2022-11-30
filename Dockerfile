FROM rust:1.65.0-slim-bullseye AS builder

WORKDIR /app
COPY . .
RUN set -eux; \
	 	cargo build --release --locked; \
		objcopy --compress-debug-sections target/release/safetyjim ./safetyjim

FROM debian:11.5-slim

RUN rm -rf /var/lib/{apt,dpkg,cache,log}/;

WORKDIR app

COPY --from=builder /app/safetyjim ./safetyjim
CMD ["./safetyjim"]