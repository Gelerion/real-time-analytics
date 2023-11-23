import streamlit as st
import pandas as pd
import time
from datetime import datetime
import plotly.graph_objects as go
# import plotly.express as px
import requests
import os
# from pinotdb import connect

# pinot_host=os.environ.get("PINOT_SERVER", "pinot-broker")
# pinot_port=os.environ.get("PINOT_PORT", 8099)
# conn = connect(pinot_host, pinot_port)

pizzashop_host_port=os.environ.get("PIZZASHOP_SERVICE", "host.docker.internal:8080")

st.set_page_config(layout="wide")
st.header("Pizza App Dashboard")

# Enable auto-refresh
now = datetime.now()
dt_string = now.strftime("%d %B %Y %H:%M:%S")
st.write(f"Last update: {dt_string}")

if not "sleep_time" in st.session_state:
    st.session_state.sleep_time = 30

if not "auto_refresh" in st.session_state:
    st.session_state.auto_refresh = True

auto_refresh = st.checkbox('Auto Refresh?', st.session_state.auto_refresh)

if auto_refresh:
    number = st.number_input('Refresh rate in seconds', value=st.session_state.sleep_time)
    st.session_state.sleep_time = number

pizza_shop_service_api = f'http://{pizzashop_host_port}' # pizza shop service

# {
#     "totalOrders": 17283,
#     "currentTimePeriod": {
#         "orders": 962,
#         "totalPrice": 4120263.0
#     },
#     "previousTimePeriod": {
#         "orders": 954,
#         "totalPrice": 4109143.0
#     }
# }
response = requests.get(f"{pizza_shop_service_api}/orders/overview").json()
# st.write(response)

# Include # of orders, Revenue and Average order value metrics based on the /overview endpoint
current = response["currentTimePeriod"]
prev = response["previousTimePeriod"]

metric1, metric2, metric3 = st.columns(3)

metric1.metric(
    label="# of Orders",
    value="{:,}".format(current["orders"]),
    delta="{:,}".format(int(current["orders"] - prev["orders"]))
)

metric2.metric(
    label="Revenue in",
    value="{:,}".format(current["totalPrice"]),
    delta="{:,}".format(int(current["totalPrice"] - prev["totalPrice"]))
)

ave_order_value_1min = current["totalPrice"] / int(current["orders"])
ave_order_value_1min_2min = (prev["totalPrice"] / int(prev["orders"]))

metric3.metric(
    label="Average order value",
    value="{:,.2f}".format(ave_order_value_1min),
    delta="{:,.2f}".format(ave_order_value_1min - ave_order_value_1min_2min)
)

# Include Orders and Revenue per minute graphs metrics based on the /ordersPerMinute endpoint

# Response example:
# {"timestamp":"2022-11-21 15:26:00","orders":598,"revenue":2589579}
# {"timestamp":"2022-11-21 15:25:00","orders":996,"revenue":4489957}
response = requests.get(f"{pizza_shop_service_api}/orders/ordersPerMinute").json()
df_ts = pd.DataFrame(response)

df_ts_melt = pd.melt(
    frame=df_ts,
    id_vars=['timestamp'],
    value_vars=['orders', 'revenue']
)

# Split the canvas into two vertical columns
col1, col2 = st.columns(2)
with col1:
    orders = df_ts_melt[df_ts_melt.variable == "orders"]

    fig = go.FigureWidget(data=[
        go.Scatter(x=orders.timestamp,
                   y=orders.value, mode='lines',
                   line={'dash': 'solid', 'color': 'green'})
    ])
    fig.update_layout(showlegend=False, title="Orders per minute",
                      margin=dict(l=0, r=0, t=40, b=0),)
    fig.update_yaxes(range=[0, df_ts["orders"].max() * 1.1])
    st.plotly_chart(fig, use_container_width=True)

with col2:
    revenue = df_ts_melt[df_ts_melt.variable == "revenue"]

    fig = go.FigureWidget(data=[
        go.Scatter(x=revenue.timestamp,
                   y=revenue.value, mode='lines',
                   line={'dash': 'solid', 'color': 'blue'})
    ])
    fig.update_layout(showlegend=False, title="Revenue per minute",
                      margin=dict(l=0, r=0, t=40, b=0),)
    fig.update_yaxes(range=[0, df_ts["revenue"].max() * 1.1])
    st.plotly_chart(fig, use_container_width=True)


if auto_refresh:
    time.sleep(number)
    st.experimental_rerun()