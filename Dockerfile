FROM rust:1.82.0-slim-bookworm AS builder

WORKDIR /app
COPY . .
RUN set -eux; \
	 	cargo build --release --locked; \
		objcopy --compress-debug-sections target/release/safetyjim ./safetyjim

FROM debian:12.7

RUN rm -rf /var/lib/{apt,dpkg,cache,log}/;

WORKDIR app

COPY --from=builder /app/safetyjim ./safetyjim
CMD ["./safetyjim"]