## What Is Streamlit?
Streamlit is an open source web framework that lets you build web applications using only Python code.
It was developed with the goal of making it easy for data scientists, data engineers, and machine learning
engineers to create and share data applications without having to learn web technologies. Streamlit 
provides a simple and intuitive API and integrates with popular Python data libraries including pandas, 
Plotly, and scikit-learn.

### Setup
```
dashboard:
    build: streamlit
    restart: unless-stopped
    container_name: dashboard
    ports:
      - "8501:8501"
    depends_on:
      - pinot-controller
    volumes:
      - ./streamlit/app.py:/workdir/app.py
    environment:
      - PINOT_SERVER
      - PINOT_PORT
```