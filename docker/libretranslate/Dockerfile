FROM libretranslate/libretranslate@sha256:1de2d7056bb8ad607a412f4563d9abe324ff632b43b5be9428bcc8e213aebb32

ADD --chown=libretranslate:libretranslate --chmod=0644 \
    --checksum=sha256:b6642c07389b156f568aa08c64140243bc1b1926f82df2559a2eae08316f84ce \
    https://data.argosopentech.com/argospm/v1/translate-th_en-1_9.argosmodel \
    /tmp/translate-th_en-1_9.argosmodel
ADD --chown=libretranslate:libretranslate --chmod=0644 \
    --checksum=sha256:b0fd94eceb04b967b91c2c7d8d1f7b5493c25362e43027a52649242385d0b2a3 \
    https://data.argosopentech.com/argospm/v1/translate-en_th-1_9.argosmodel \
    /tmp/translate-en_th-1_9.argosmodel
ADD --chown=libretranslate:libretranslate --chmod=0644 \
    --checksum=sha256:6fa9f3a3b201687bd43e329b2b0736789efc97b37883b5b759b31988dca9f353 \
    https://github.com/LibreTranslate/MiniSBD/releases/download/v0.0.1/en.onnx \
    /tmp/en.onnx
ADD --chown=libretranslate:libretranslate --chmod=0644 \
    --checksum=sha256:62b94ddf5af61ddde4e99dbb4087be71135e26b897e898e5bcb8679842691450 \
    https://github.com/LibreTranslate/MiniSBD/releases/download/v0.0.1/th.onnx \
    /tmp/th.onnx

RUN /app/venv/bin/python -c \
      "from argostranslate.package import install_from_path; \
       install_from_path('/tmp/translate-th_en-1_9.argosmodel'); \
       install_from_path('/tmp/translate-en_th-1_9.argosmodel')" \
    && mkdir -p /home/libretranslate/.local/share/argos-translate/minisbd \
    && mv /tmp/en.onnx /tmp/th.onnx \
      /home/libretranslate/.local/share/argos-translate/minisbd/ \
    && rm /tmp/*.argosmodel
