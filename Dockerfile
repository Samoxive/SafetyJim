FROM rust:1.97.0-slim-bookworm AS builder

WORKDIR /app
COPY . .
RUN set -eux; \
	 	cargo build --release --locked; \
		objcopy --compress-debug-sections target/release/safetyjim ./safetyjim

FROM debian:12.2-slim

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates; \
    rm -rf /var/lib/apt/lists/* /var/cache/apt/* /var/log/*

WORKDIR app

COPY --from=builder /app/safetyjim ./safetyjim
CMD ["./safetyjim"]