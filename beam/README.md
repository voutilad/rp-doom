# Apache Beam pipeline

## For macOS/arm64 Users!
Apache Beam 2.46.0 uses an older version of PyArrow that doesn't exist for macOS on Apple Silicon. I've built a pre-release of v2.47.0 and stashed it as a Python wheel file in `./apple`. Assuming you've activated your virtual environment `venv`:

```
(venv) $ pip install -U wheel
(venv) $ pip install ./apple/apache_beam-2.47.0.dev0-py3-none-any.whl
```
