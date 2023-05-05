.PHONY: all clean

# todo: split up into multiple targets
all:
	openssl genrsa -out ca.key 2048 && chmod 400 ca.key
	openssl req -new -x509 -config ca.cnf -key ca.key \
		-days 365 -batch -out ca.crt
	openssl genrsa -out broker.key 2048
	openssl req -new -config broker.cnf -key broker.key -out broker.csr
	openssl x509 -req -in broker.csr -CA ca.crt -CAkey ca.key \
		-extfile broker.cnf -extensions extensions \
		-CAcreateserial -out broker.crt -days 365 -sha256

clean:
	rm -f *.key *.crt *.csr
