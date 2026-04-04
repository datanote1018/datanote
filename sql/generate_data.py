#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
电商业务库测试数据生成器
用法:
    python generate_data.py init     # 初始化6个月历史数据（跑一次）
    python generate_data.py daily    # 模拟当天新增数据（可重复跑）
"""

import sys
import random
import string
import hashlib
from datetime import datetime, timedelta, date
import mysql.connector

# ========== 数据库连接 ==========
DB_CONFIG = {
    'host': '127.0.0.1',
    'port': 3306,
    'user': 'root',
    'password': 'root',
}

# ========== 基础数据常量 ==========

# 中文姓
SURNAMES = ['王','李','张','刘','陈','杨','赵','黄','周','吴','徐','孙','胡','朱','高',
            '林','何','郭','马','罗','梁','宋','郑','谢','韩','唐','冯','于','董','萧',
            '程','曹','袁','邓','许','傅','沈','曾','彭','吕','苏','卢','蒋','蔡','贾',
            '丁','魏','薛','叶','阎','余','潘','杜','戴','夏','钟','汪','田','任','姜']

# 中文名（单字/双字）
NAME_CHARS = ['伟','芳','娜','敏','静','丽','强','磊','军','洋','勇','艳','杰','娟','涛',
              '明','超','秀英','桂英','玉兰','婷','宇','欣','鑫','浩','然','子涵','雨泽',
              '思远','志强','建华','文博','天翼','晨曦','雅琴','美玲','晓峰','海燕','小红']

# 省份城市映射（按人口权重）
PROVINCE_CITY = {
    '广东': (['广州','深圳','东莞','佛山','珠海','惠州','中山','江门','湛江','汕头'], 12),
    '江苏': (['南京','苏州','无锡','常州','南通','徐州','扬州','盐城','镇江','泰州'], 10),
    '浙江': (['杭州','宁波','温州','绍兴','嘉兴','金华','台州','湖州','丽水','衢州'], 8),
    '山东': (['济南','青岛','烟台','潍坊','临沂','济宁','淄博','泰安','威海','日照'], 8),
    '河南': (['郑州','洛阳','南阳','许昌','周口','新乡','商丘','信阳','驻马店','焦作'], 7),
    '四川': (['成都','绵阳','德阳','宜宾','南充','泸州','达州','乐山','内江','自贡'], 6),
    '湖北': (['武汉','宜昌','襄阳','荆州','黄冈','十堰','孝感','黄石','咸宁','鄂州'], 5),
    '湖南': (['长沙','株洲','湘潭','衡阳','岳阳','常德','邵阳','益阳','郴州','永州'], 5),
    '河北': (['石家庄','唐山','保定','廊坊','邯郸','秦皇岛','沧州','邢台','张家口','衡水'], 5),
    '北京': (['北京'], 8),
    '上海': (['上海'], 8),
    '福建': (['福州','厦门','泉州','漳州','莆田','宁德','龙岩','三明','南平'], 4),
    '安徽': (['合肥','芜湖','蚌埠','阜阳','淮南','安庆','马鞍山','宿州','滁州'], 4),
    '辽宁': (['沈阳','大连','鞍山','抚顺','锦州','营口','盘锦','丹东','葫芦岛'], 3),
    '陕西': (['西安','咸阳','宝鸡','渭南','汉中','延安','安康','榆林','商洛'], 3),
    '重庆': (['重庆'], 4),
    '江西': (['南昌','赣州','九江','宜春','吉安','上饶','景德镇','萍乡','新余'], 3),
    '云南': (['昆明','曲靖','大理','红河','玉溪','楚雄','文山','普洱','临沧'], 2),
    '广西': (['南宁','柳州','桂林','梧州','北海','玉林','钦州','百色','贵港'], 2),
    '天津': (['天津'], 3),
}

DISTRICTS = ['朝阳区','海淀区','西湖区','南山区','天河区','雨花台区','武侯区',
             '江岸区','开发区','高新区','经济技术开发区','新区','老城区']

STREETS = ['中山路','人民路','建设路','解放路','和平路','文化路','科技路',
           '长江路','黄河路','南京路','北京路','上海路','学院路','创业路']

CHANNELS = ['APP', 'H5', '小程序', 'PC']
DEVICES = ['iOS', 'Android', 'PC']
GENDERS = ['M', 'F', 'U']
USER_LEVELS = ['普通', '铜牌', '银牌', '金牌', '钻石']
LABELS = ['家', '公司', '学校', None]

# 商品分类体系
CATEGORY_TREE = {
    '手机数码': {
        '手机通讯': ['智能手机', '功能手机', '对讲机'],
        '手机配件': ['手机壳', '充电器', '数据线', '钢化膜', '耳机'],
        '数码配件': ['存储卡', '移动电源', '自拍杆'],
    },
    '电脑办公': {
        '笔记本': ['轻薄本', '游戏本', '商务本'],
        '台式机': ['品牌台式机', '组装电脑'],
        '电脑配件': ['鼠标', '键盘', '显示器', '内存条', '硬盘'],
    },
    '家用电器': {
        '大家电': ['冰箱', '洗衣机', '空调', '电视'],
        '厨房电器': ['微波炉', '电饭煲', '电磁炉', '烤箱', '豆浆机'],
        '个护健康': ['电动牙刷', '剃须刀', '吹风机'],
    },
    '服饰鞋包': {
        '男装': ['T恤', '衬衫', '外套', '裤子', '卫衣'],
        '女装': ['连衣裙', '衬衫', '外套', '半身裙', '卫衣'],
        '鞋靴': ['运动鞋', '皮鞋', '靴子', '凉鞋', '拖鞋'],
        '箱包': ['双肩包', '手提包', '旅行箱', '钱包'],
    },
    '食品饮料': {
        '休闲零食': ['坚果', '饼干', '糖果', '肉脯', '膨化食品'],
        '饮料': ['矿泉水', '果汁', '碳酸饮料', '茶饮料', '咖啡'],
        '生鲜': ['水果', '蔬菜', '肉禽蛋', '海鲜'],
    },
    '美妆护肤': {
        '面部护肤': ['面膜', '精华', '面霜', '防晒', '洁面'],
        '彩妆': ['口红', '粉底', '眼影', '腮红', '眉笔'],
        '个人护理': ['洗发水', '沐浴露', '牙膏', '护手霜'],
    },
    '家居家装': {
        '家纺': ['床品套件', '枕头', '毛巾', '窗帘'],
        '灯具': ['吸顶灯', '台灯', '落地灯', '壁灯'],
        '家具': ['沙发', '餐桌', '书桌', '衣柜', '床'],
    },
    '母婴玩具': {
        '奶粉辅食': ['婴幼儿奶粉', '米粉', '果泥'],
        '纸尿裤': ['婴儿纸尿裤', '拉拉裤', '湿巾'],
        '玩具': ['积木', '遥控车', '毛绒玩具', '益智玩具'],
    },
    '运动户外': {
        '运动服饰': ['运动T恤', '运动裤', '运动内衣'],
        '运动鞋': ['跑步鞋', '篮球鞋', '足球鞋'],
        '户外装备': ['帐篷', '睡袋', '登山杖', '冲锋衣'],
    },
    '图书文具': {
        '图书': ['小说', '教辅', '经管', '计算机', '童书'],
        '文具': ['笔', '本子', '文件夹', '计算器'],
    },
}

BRANDS = [
    '华为','小米','苹果','三星','OPPO','vivo','联想','戴尔','惠普','华硕',
    '海尔','美的','格力','TCL','海信','耐克','阿迪达斯','安踏','李宁','优衣库',
    '雀巢','伊利','蒙牛','农夫山泉','可口可乐','兰蔻','雅诗兰黛','欧莱雅','SK-II','完美日记',
]

WAREHOUSES = [
    ('华北仓', '北京', '北京', '大兴区物流园区A栋', '自营仓'),
    ('华东仓', '江苏', '南京', '江宁区电商物流基地B栋', '自营仓'),
    ('华南仓', '广东', '广州', '黄埔区保税物流中心C栋', '自营仓'),
    ('华中仓', '湖北', '武汉', '东西湖区物流产业园D栋', '自营仓'),
    ('西南仓', '四川', '成都', '双流区航空物流园E栋', '自营仓'),
]

EXPRESS_COMPANIES = ['SF', 'YTO', 'ZTO', 'STO', 'JD', 'YD']

COUPON_TEMPLATES = [
    ('新人专享满100减20', '满减', 100, 20, None, '全场', 5000),
    ('满200减30优惠券', '满减', 200, 30, None, '全场', 10000),
    ('满500减80优惠券', '满减', 500, 80, None, '全场', 3000),
    ('数码品类满1000减150', '满减', 1000, 150, None, '品类', 2000),
    ('服饰85折券', '折扣', 0, None, 0.85, '品类', 5000),
    ('运费券满59免邮', '运费券', 59, 10, None, '全场', 8000),
    ('无门槛5元券', '无门槛', 0, 5, None, '全场', 20000),
    ('家电满2000减300', '满减', 2000, 300, None, '品类', 1000),
    ('美妆9折券', '折扣', 0, None, 0.90, '品类', 3000),
    ('会员专属满300减50', '满减', 300, 50, None, '全场', 2000),
]

ORDER_SOURCES = ['PC', 'APP', 'H5', '小程序']
PAY_TYPES = ['WECHAT', 'ALIPAY', 'BALANCE']
REFUND_REASONS = ['质量问题', '发错货', '不想要了', '少件/漏发', '与描述不符', '其他']
CANCEL_REASONS = ['不想买了', '信息填错了', '重复下单', '其他']

# ========== 工具函数 ==========

def get_conn(db=None):
    cfg = DB_CONFIG.copy()
    if db:
        cfg['database'] = db
    return mysql.connector.connect(**cfg)

def batch_insert(conn, sql, data, batch_size=2000):
    cursor = conn.cursor()
    for i in range(0, len(data), batch_size):
        cursor.executemany(sql, data[i:i+batch_size])
    conn.commit()
    cursor.close()

def random_name():
    return random.choice(SURNAMES) + random.choice(NAME_CHARS)

def mask_name(name):
    """姓名脱敏：张* 或 张**"""
    return name[0] + '*' * (len(name) - 1)

def mask_phone(phone):
    """手机号脱敏：138****6789"""
    return phone[:3] + '****' + phone[7:]

def mask_idcard(idcard):
    """身份证脱敏：110***********1234"""
    return idcard[:3] + '***********' + idcard[-4:]

def gen_phone():
    """生成原始手机号（11位）"""
    prefixes = ['130','131','132','133','134','135','136','137','138','139',
                '150','151','152','153','155','156','157','158','159',
                '170','171','172','173','175','176','177','178',
                '180','181','182','183','184','185','186','187','188','189']
    return random.choice(prefixes) + ''.join([str(random.randint(0,9)) for _ in range(8)])

def gen_idcard():
    """生成模拟身份证号（非真实，仅格式正确）"""
    area_codes = ['110101','310101','440305','330102','320105','500101',
                  '420106','430104','510107','610103','330106','350102']
    area = random.choice(area_codes)
    year = random.randint(1970, 2005)
    month = random.randint(1, 12)
    day = random.randint(1, 28)
    seq = random.randint(1, 999)
    base = f"{area}{year}{month:02d}{day:02d}{seq:03d}"
    # 简化校验码
    check = str(random.randint(0, 9)) if random.random() > 0.1 else 'X'
    return base + check

def gen_email(login_name):
    domains = ['qq.com', '163.com', '126.com', 'gmail.com', 'outlook.com', 'foxmail.com']
    return f"{login_name}@{random.choice(domains)}"

def random_province_city():
    """按人口权重随机选省市"""
    provinces = list(PROVINCE_CITY.keys())
    weights = [PROVINCE_CITY[p][1] for p in provinces]
    province = random.choices(provinces, weights=weights, k=1)[0]
    city = random.choice(PROVINCE_CITY[province][0])
    return province, city

def random_address():
    return random.choice(STREETS) + str(random.randint(1,200)) + '号' + \
           random.choice(['', '小区', '大厦', '广场', '花园']) + \
           str(random.randint(1,30)) + '栋' + str(random.randint(1,30)) + '0' + str(random.randint(1,9))

def random_datetime(start, end):
    delta = end - start
    secs = int(delta.total_seconds())
    return start + timedelta(seconds=random.randint(0, max(secs, 1)))

def random_order_time(day):
    """按真实分布生成一天内的下单时间（10点和20-22点高峰）"""
    # 高峰时段权重
    hour_weights = [1,1,1,1,1,2,3,5,8,12,15,12,10,8,8,10,12,14,16,18,20,18,12,5]
    hour = random.choices(range(24), weights=hour_weights, k=1)[0]
    minute = random.randint(0, 59)
    second = random.randint(0, 59)
    return datetime(day.year, day.month, day.day, hour, minute, second)

def gen_order_no():
    """生成订单编号"""
    now = datetime.now()
    return now.strftime('%Y%m%d%H%M%S') + ''.join([str(random.randint(0,9)) for _ in range(6)])

def gen_tracking_no(company):
    """生成快递单号"""
    prefix_map = {'SF':'SF','YTO':'YT','ZTO':'ZT','STO':'ST','JD':'JD','YD':'YD'}
    prefix = prefix_map.get(company, 'EX')
    return prefix + ''.join([str(random.randint(0,9)) for _ in range(12)])

# ========== 各模块数据生成 ==========

class DataGenerator:
    def __init__(self):
        self.user_ids = []       # 存 user_id 业务编号列表
        self.sku_list = []       # [(sku_id, spu_id, price, sku_name), ...]
        self.warehouse_ids = []
        self.coupon_ids = []
        self.activity_ids = []
        self.category3_ids = []

    # ---------- 商品中心 ----------
    def gen_product_center(self):
        conn = get_conn('product_center')
        cursor = conn.cursor()
        print('[商品中心] 生成分类...')

        cat1_id = 0
        cat2_id = 0
        cat3_id = 0
        cat1_data = []
        cat2_data = []
        cat3_data = []

        for c1_name, c2_dict in CATEGORY_TREE.items():
            cat1_id += 1
            cat1_data.append((cat1_id, c1_name))
            for c2_name, c3_list in c2_dict.items():
                cat2_id += 1
                cat2_data.append((cat2_id, c2_name, cat1_id))
                for c3_name in c3_list:
                    cat3_id += 1
                    cat3_data.append((cat3_id, c3_name, cat2_id))
                    self.category3_ids.append(cat3_id)

        cursor.executemany("INSERT INTO base_category1 (id, name) VALUES (%s,%s)", cat1_data)
        cursor.executemany("INSERT INTO base_category2 (id, name, category1_id) VALUES (%s,%s,%s)", cat2_data)
        cursor.executemany("INSERT INTO base_category3 (id, name, category2_id) VALUES (%s,%s,%s)", cat3_data)

        print('[商品中心] 生成品牌...')
        brand_data = [(i+1, b) for i, b in enumerate(BRANDS)]
        cursor.executemany("INSERT INTO base_trademark (id, tm_name) VALUES (%s,%s)", brand_data)

        print('[商品中心] 生成销售属性...')
        sale_attrs = [(1,'颜色'),(2,'尺码'),(3,'版本')]
        cursor.executemany("INSERT INTO base_sale_attr (id, name) VALUES (%s,%s)", sale_attrs)

        print('[商品中心] 生成SPU和SKU...')
        spu_id = 0
        sku_id = 0
        spu_data = []
        sku_data = []
        sku_sale_data = []
        poster_data = []

        colors = ['黑色','白色','蓝色','红色','灰色','金色','银色','绿色']
        sizes = ['S','M','L','XL','XXL']
        versions = ['标准版','高配版','旗舰版']

        for c1_name, c2_dict in CATEGORY_TREE.items():
            for c2_name, c3_list in c2_dict.items():
                for c3_name in c3_list:
                    # 每个三级分类生成1-3个SPU
                    n_spu = random.randint(1, 3)
                    for _ in range(n_spu):
                        spu_id += 1
                        brand_id = random.randint(1, len(BRANDS))
                        brand_name = BRANDS[brand_id - 1]
                        spu_name = f"{brand_name} {c3_name}"
                        cat3_idx = self.category3_ids.index(cat3_id) + 1 if cat3_id in self.category3_ids else 1
                        # 找到对应的 category3 id
                        c3_id_val = None
                        tmp_c3 = 0
                        for _c1, _c2d in CATEGORY_TREE.items():
                            for _c2, _c3l in _c2d.items():
                                for _c3 in _c3l:
                                    tmp_c3 += 1
                                    if _c3 == c3_name and _c2 == c2_name:
                                        c3_id_val = tmp_c3
                        if c3_id_val is None:
                            c3_id_val = 1

                        spu_data.append((spu_id, spu_name, c3_id_val, brand_id))
                        poster_data.append((spu_id, f'https://img.example.com/spu/{spu_id}/poster.jpg'))

                        # 每个SPU生成2-4个SKU
                        base_price = random.choice([29.9,49.9,99,129,199,299,499,699,999,
                                                     1299,1999,2999,4999,6999])
                        n_sku = random.randint(2, 4)
                        for j in range(n_sku):
                            sku_id += 1
                            color = random.choice(colors)
                            if c1_name == '服饰鞋包':
                                size = random.choice(sizes)
                                attr_val = f"{color}/{size}"
                                sale_attr_id = 2
                                sale_attr_val = size
                            elif c1_name in ('手机数码', '电脑办公'):
                                ver = random.choice(versions)
                                attr_val = f"{color}/{ver}"
                                sale_attr_id = 3
                                sale_attr_val = ver
                            else:
                                attr_val = color
                                sale_attr_id = 1
                                sale_attr_val = color

                            price = round(base_price * random.uniform(0.9, 1.3), 2)
                            market_price = round(price * random.uniform(1.1, 1.5), 2)
                            cost_price = round(price * random.uniform(0.4, 0.7), 2)
                            sku_name = f"{spu_name} {attr_val}"
                            weight = round(random.uniform(0.1, 20.0), 2)

                            sku_data.append((sku_id, spu_id, sku_name,
                                            f'https://img.example.com/sku/{sku_id}/main.jpg',
                                            price, market_price, cost_price, weight,
                                            f'69{sku_id:010d}',
                                            random.randint(0, 5000)))
                            self.sku_list.append((sku_id, spu_id, price, sku_name))

                            # 颜色销售属性
                            sku_sale_data.append((sku_id, spu_id, 1, '颜色', color))
                            # 第二个销售属性
                            if sale_attr_id != 1:
                                sku_sale_data.append((sku_id, spu_id, sale_attr_id,
                                                     '尺码' if sale_attr_id==2 else '版本',
                                                     sale_attr_val))

        batch_insert(conn, "INSERT INTO spu_info (id,spu_name,category3_id,tm_id) VALUES (%s,%s,%s,%s)", spu_data)
        batch_insert(conn, "INSERT INTO spu_poster (spu_id,img_url) VALUES (%s,%s)", poster_data)
        batch_insert(conn, """INSERT INTO sku_info (id,spu_id,sku_name,sku_default_img,price,
                     market_price,cost_price,weight,barcode,sale_count)
                     VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""", sku_data)
        batch_insert(conn, """INSERT INTO sku_sale_attr_value (sku_id,spu_id,sale_attr_id,
                     sale_attr_name,sale_attr_value_name) VALUES (%s,%s,%s,%s,%s)""", sku_sale_data)
        conn.commit()
        conn.close()
        print(f'[商品中心] 完成: {len(spu_data)} SPU, {len(sku_data)} SKU')

    # ---------- 用户中心 ----------
    def gen_user_center(self, count=10000, start_date=None, end_date=None):
        if start_date is None:
            start_date = datetime.now() - timedelta(days=180)
        if end_date is None:
            end_date = datetime.now()

        conn = get_conn('user_center')
        print(f'[用户中心] 生成 {count} 个用户...')

        # 查已有最大编号
        cursor = conn.cursor()
        cursor.execute("SELECT MAX(id) FROM user_info")
        max_id = cursor.fetchone()[0] or 0
        cursor.close()

        user_data = []
        auth_data = []
        address_data = []
        login_data = []
        favor_data = []

        for i in range(count):
            idx = max_id + i + 1
            uid = f'U{100000 + idx}'
            name = random_name()
            login_name = f'user_{idx}'
            phone = gen_phone()
            gender = random.choices(GENDERS, weights=[45,45,10], k=1)[0]
            level = random.choices(USER_LEVELS, weights=[50,25,15,7,3], k=1)[0]
            channel = random.choice(CHANNELS)
            birthday = date(random.randint(1970,2005), random.randint(1,12), random.randint(1,28))
            reg_time = random_datetime(start_date, end_date)
            ip = f'192.168.{random.randint(1,254)}.{random.randint(1,254)}'

            user_data.append((uid, login_name, name,
                             hashlib.md5(b'123456').hexdigest(),
                             mask_phone(phone),
                             gen_email(login_name), gender, birthday, level,
                             channel, ip, reg_time))
            self.user_ids.append(uid)

            # 实名认证（80%的用户已认证）
            if random.random() < 0.8:
                idcard = gen_idcard()
                auth_data.append((uid, mask_name(name), mask_idcard(idcard),
                                 '已认证', reg_time + timedelta(hours=random.randint(1,48))))

            # 收货地址（1-3个）
            n_addr = random.choices([1,2,3], weights=[50,35,15], k=1)[0]
            for j in range(n_addr):
                province, city = random_province_city()
                district = random.choice(DISTRICTS)
                address_data.append((uid, name if j==0 else random_name(),
                                    mask_phone(gen_phone()),
                                    province, city, district, random_address(),
                                    random.choice(LABELS),
                                    1 if j==0 else 0, reg_time))

            # 登录日志（每用户5-20条）
            n_login = random.randint(5, 20)
            for _ in range(n_login):
                lt = random_datetime(reg_time, end_date)
                login_data.append((uid, lt,
                                  f'192.168.{random.randint(1,254)}.{random.randint(1,254)}',
                                  random.choice(DEVICES), random.choice(CHANNELS)))

            # 收藏（0-5个SKU）
            if self.sku_list:
                n_fav = random.randint(0, 5)
                fav_skus = random.sample(self.sku_list, min(n_fav, len(self.sku_list)))
                for s in fav_skus:
                    favor_data.append((uid, s[0], random_datetime(reg_time, end_date)))

        batch_insert(conn, """INSERT INTO user_info (user_id,login_name,nickname,password,phone,
                     email,gender,birthday,user_level,register_channel,register_ip,create_time)
                     VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""", user_data)

        if auth_data:
            batch_insert(conn, """INSERT INTO user_real_auth (user_id,real_name,id_card_no,
                         auth_status,auth_time) VALUES (%s,%s,%s,%s,%s)""", auth_data)

        if address_data:
            batch_insert(conn, """INSERT INTO user_address (user_id,receiver_name,receiver_phone,
                         province,city,district,detail_address,label,is_default,create_time)
                         VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""", address_data)

        if login_data:
            batch_insert(conn, """INSERT INTO user_login_log (user_id,login_time,login_ip,
                         device_type,login_channel) VALUES (%s,%s,%s,%s,%s)""", login_data)

        if favor_data:
            # 去重
            seen = set()
            unique_favor = []
            for f in favor_data:
                key = (f[0], f[1])
                if key not in seen:
                    seen.add(key)
                    unique_favor.append(f)
            batch_insert(conn, "INSERT INTO favor_info (user_id,sku_id,create_time) VALUES (%s,%s,%s)",
                        unique_favor)

        conn.commit()
        conn.close()
        print(f'[用户中心] 完成: {len(user_data)} 用户, {len(auth_data)} 实名, '
              f'{len(address_data)} 地址, {len(login_data)} 登录日志')

    # ---------- 仓储中心 ----------
    def gen_warehouse_center(self):
        conn = get_conn('warehouse_center')
        print('[仓储中心] 生成仓库和库存...')

        wh_data = []
        for i, (name, province, city, addr, wtype) in enumerate(WAREHOUSES):
            wh_data.append((i+1, name, province, city, addr, wtype))
            self.warehouse_ids.append(i+1)

        cursor = conn.cursor()
        cursor.executemany("""INSERT INTO warehouse_info (id,name,province,city,address,type)
                     VALUES (%s,%s,%s,%s,%s,%s)""", wh_data)

        # 分仓库存
        stock_data = []
        for sku_id, _, _, _ in self.sku_list:
            for wid in self.warehouse_ids:
                qty = random.randint(50, 500)
                stock_data.append((wid, sku_id, qty, 0, qty))

        batch_insert(conn, """INSERT INTO warehouse_sku_stock (warehouse_id,sku_id,stock_qty,
                     locked_qty,available_qty) VALUES (%s,%s,%s,%s,%s)""", stock_data)
        conn.commit()
        conn.close()
        print(f'[仓储中心] 完成: {len(WAREHOUSES)} 仓库, {len(stock_data)} 库存记录')

    # ---------- 营销中心 ----------
    def gen_marketing_center(self, start_date=None, end_date=None):
        if start_date is None:
            start_date = datetime.now() - timedelta(days=180)
        if end_date is None:
            end_date = datetime.now() + timedelta(days=30)

        conn = get_conn('marketing_center')
        print('[营销中心] 生成优惠券和活动...')

        coupon_data = []
        for i, (name, ctype, cond, benefit, discount, scope, total) in enumerate(COUPON_TEMPLATES):
            cid = i + 1
            taken = int(total * random.uniform(0.3, 0.8))
            used = int(taken * random.uniform(0.2, 0.6))
            s_time = random_datetime(start_date, end_date - timedelta(days=30))
            e_time = s_time + timedelta(days=random.choice([7,15,30,60]))
            status = '启用' if e_time > datetime.now() else '过期'
            coupon_data.append((cid, name, ctype, cond if cond > 0 else None,
                               benefit, discount, scope, total, taken, used, 1,
                               s_time, e_time, status))
            self.coupon_ids.append(cid)

        cursor = conn.cursor()
        cursor.executemany("""INSERT INTO coupon_info (id,coupon_name,coupon_type,condition_amount,
                     benefit_amount,benefit_discount,scope_type,total_count,taken_count,used_count,
                     per_user_limit,start_time,end_time,status) VALUES
                     (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""", coupon_data)

        # 领券记录
        coupon_use_data = []
        for uid in self.user_ids:
            n_coupon = random.randint(0, 3)
            for _ in range(n_coupon):
                cid = random.choice(self.coupon_ids)
                get_time = random_datetime(start_date, end_date)
                status = random.choices(['未使用','已使用','已过期'], weights=[30,50,20], k=1)[0]
                use_time = get_time + timedelta(days=random.randint(1,15)) if status == '已使用' else None
                coupon_use_data.append((cid, uid, None, status, get_time, use_time))

        batch_insert(conn, """INSERT INTO coupon_use (coupon_id,user_id,order_id,coupon_status,
                     get_time,use_time) VALUES (%s,%s,%s,%s,%s,%s)""", coupon_use_data)

        # 活动
        activities = [
            ('618年中大促', '满减', '全场满300减50，满500减100', 30),
            ('双11狂欢节', '满减', '全场满200减30，满400减60', 15),
            ('春季焕新', '折扣', '指定商品85折', 20),
            ('新品秒杀', '秒杀', '限时限量秒杀', 3),
            ('周末特惠', '折扣', '每周末指定品类9折', 60),
        ]
        act_data = []
        act_rule_data = []
        act_sku_data = []
        for i, (aname, atype, adesc, duration) in enumerate(activities):
            aid = i + 1
            s_time = random_datetime(start_date, end_date - timedelta(days=duration))
            e_time = s_time + timedelta(days=duration)
            status = '进行中' if s_time <= datetime.now() <= e_time else ('已结束' if e_time < datetime.now() else '未开始')
            act_data.append((aid, aname, atype, adesc, s_time, e_time, status))
            self.activity_ids.append(aid)

            if atype == '满减':
                act_rule_data.append((aid, atype, 300, 50, None, 1))
                act_rule_data.append((aid, atype, 500, 100, None, 2))
            elif atype == '折扣':
                act_rule_data.append((aid, atype, None, None, 0.85, 1))
            elif atype == '秒杀':
                act_rule_data.append((aid, atype, None, None, 0.50, 1))

            # 活动商品
            n_sku = random.randint(10, 30)
            chosen = random.sample(self.sku_list, min(n_sku, len(self.sku_list)))
            for sku_id, _, price, _ in chosen:
                act_price = round(price * random.uniform(0.5, 0.9), 2)
                act_stock = random.randint(50, 200)
                act_sku_data.append((aid, sku_id, act_price, act_stock))

        cursor.executemany("""INSERT INTO activity_info (id,activity_name,activity_type,
                     activity_desc,start_time,end_time,status) VALUES (%s,%s,%s,%s,%s,%s,%s)""", act_data)
        cursor.executemany("""INSERT INTO activity_rule (activity_id,activity_type,condition_amount,
                     benefit_amount,benefit_discount,benefit_level) VALUES (%s,%s,%s,%s,%s,%s)""", act_rule_data)
        batch_insert(conn, """INSERT INTO activity_sku (activity_id,sku_id,activity_price,
                     activity_stock) VALUES (%s,%s,%s,%s)""", act_sku_data)

        conn.commit()
        conn.close()
        print(f'[营销中心] 完成: {len(coupon_data)} 优惠券, {len(coupon_use_data)} 领券记录, '
              f'{len(act_data)} 活动')

    # ---------- 订单+支付+物流+评价（核心） ----------
    def gen_orders(self, order_count=50000, start_date=None, end_date=None):
        if start_date is None:
            start_date = datetime.now() - timedelta(days=180)
        if end_date is None:
            end_date = datetime.now()

        print(f'[订单/支付/物流] 生成 {order_count} 个订单...')

        conn_order = get_conn('order_center')
        conn_pay = get_conn('pay_center')
        conn_wh = get_conn('warehouse_center')
        conn_base = get_conn('base_data')

        order_data = []
        sub_order_data = []
        detail_data = []
        status_log_data = []
        cart_data = []
        refund_data = []
        payment_data = []
        refund_pay_data = []
        delivery_data = []
        track_data = []
        comment_data = []
        stock_log_data = []
        cost_data_set = set()
        cost_data = []

        # 生成日期范围内的天数列表
        days = []
        d = start_date.date() if isinstance(start_date, datetime) else start_date
        end_d = end_date.date() if isinstance(end_date, datetime) else end_date
        while d <= end_d:
            days.append(d)
            d += timedelta(days=1)

        sub_order_counter = 0
        detail_counter = 0
        delivery_counter = 0
        track_counter = 0

        for order_idx in range(order_count):
            if order_idx % 10000 == 0 and order_idx > 0:
                print(f'  ... 已生成 {order_idx}/{order_count} 订单')

            order_id = order_idx + 1
            user_id = random.choice(self.user_ids)
            day = random.choice(days)
            create_time = random_order_time(day)
            order_no = f'ORD{create_time.strftime("%Y%m%d")}{order_id:08d}'

            # 随机选购商品（1-5个）
            n_items = random.choices([1,2,3,4,5], weights=[30,35,20,10,5], k=1)[0]
            items = random.sample(self.sku_list, min(n_items, len(self.sku_list)))

            total_amount = 0
            item_details = []
            for sku_id, spu_id, price, sku_name in items:
                qty = random.choices([1,2,3], weights=[60,30,10], k=1)[0]
                line_total = round(price * qty, 2)
                total_amount += line_total
                item_details.append((sku_id, spu_id, sku_name, qty, price, line_total))

            # 优惠计算
            discount_amount = 0
            coupon_id = None
            activity_id = None
            if random.random() < 0.3 and self.coupon_ids:
                coupon_id = random.choice(self.coupon_ids)
                discount_amount = round(total_amount * random.uniform(0.05, 0.15), 2)
            if random.random() < 0.2 and self.activity_ids:
                activity_id = random.choice(self.activity_ids)
                if discount_amount == 0:
                    discount_amount = round(total_amount * random.uniform(0.05, 0.10), 2)

            freight = 0 if total_amount >= 59 else round(random.uniform(5, 15), 2)
            pay_amount = round(max(total_amount - discount_amount + freight, 0.01), 2)

            # 订单状态分布
            status_roll = random.random()
            if status_roll < 0.05:
                pay_status = '未支付'
                order_flow = ['未支付']  # 待支付
            elif status_roll < 0.10:
                pay_status = '未支付'
                order_flow = ['未支付', '已取消']  # 取消
            else:
                pay_status = '已支付'
                sub_roll = random.random()
                if sub_roll < 0.6:
                    order_flow = ['未支付', '已支付', '已发货', '已签收', '已完成']
                elif sub_roll < 0.8:
                    order_flow = ['未支付', '已支付', '已发货', '已签收']
                elif sub_roll < 0.9:
                    order_flow = ['未支付', '已支付', '已发货']
                else:
                    order_flow = ['未支付', '已支付']

            pay_time = create_time + timedelta(minutes=random.randint(1, 30)) if pay_status == '已支付' else None
            cancel_time = create_time + timedelta(minutes=random.randint(5, 60)) if '已取消' in order_flow else None
            expire_time = create_time + timedelta(minutes=30)

            province, city = random_province_city()
            pay_type = random.choice(PAY_TYPES)
            source = random.choice(ORDER_SOURCES)

            order_data.append((order_id, order_no, user_id, total_amount, discount_amount,
                              freight, pay_amount, '普通', pay_type, pay_status,
                              coupon_id, activity_id,
                              random_name(), mask_phone(gen_phone()),
                              province, city, random.choice(DISTRICTS), random_address(),
                              None, source,
                              random.choice(CANCEL_REASONS) if cancel_time else None,
                              create_time, pay_time, cancel_time, expire_time))

            # 拆单：20%的订单拆成2个子订单
            if len(item_details) >= 2 and random.random() < 0.2:
                split_at = random.randint(1, len(item_details) - 1)
                sub_groups = [item_details[:split_at], item_details[split_at:]]
            else:
                sub_groups = [item_details]

            for sg_idx, sg_items in enumerate(sub_groups):
                sub_order_counter += 1
                sub_id = sub_order_counter
                sub_no = f'{order_no}S{sg_idx+1}'
                wh_id = random.choice(self.warehouse_ids)
                sub_total = sum(x[5] for x in sg_items)
                sub_freight = round(freight / len(sub_groups), 2) if sg_idx == 0 else 0

                last_status = order_flow[-1]
                sub_status_map = {
                    '未支付': '待付款', '已支付': '已付款', '已取消': '已取消',
                    '已发货': '已发货', '已签收': '已签收', '已完成': '已完成'
                }
                sub_status = sub_status_map.get(last_status, '待付款')

                deliver_time = None
                receive_time = None
                complete_time = None
                if '已发货' in order_flow and pay_time:
                    deliver_time = pay_time + timedelta(hours=random.randint(2, 48))
                if '已签收' in order_flow and deliver_time:
                    receive_time = deliver_time + timedelta(days=random.randint(1, 5))
                if '已完成' in order_flow and receive_time:
                    complete_time = receive_time + timedelta(days=random.randint(0, 7))

                sub_order_data.append((sub_id, order_id, sub_no, wh_id, sub_total,
                                      sub_freight, sub_status, deliver_time,
                                      receive_time, complete_time, create_time))

                # 明细行
                for sku_id, spu_id, sku_name, qty, price, line_total in sg_items:
                    detail_counter += 1
                    split_discount = round(discount_amount * (line_total / total_amount), 2) if total_amount > 0 else 0
                    detail_data.append((detail_counter, order_id, sub_id, sku_id,
                                       sku_name,
                                       f'https://img.example.com/sku/{sku_id}/main.jpg',
                                       None, qty, price, line_total, split_discount, 0, create_time))

                    # 成本数据
                    if sku_id not in cost_data_set:
                        cost_data_set.add(sku_id)
                        cost_data.append((sku_id, round(price * random.uniform(0.4, 0.7), 2)))

                # 物流
                if deliver_time:
                    delivery_counter += 1
                    d_id = delivery_counter
                    company = random.choice(EXPRESS_COMPANIES)
                    tracking = gen_tracking_no(company)
                    d_status = '已签收' if receive_time else '运输中'
                    delivery_data.append((d_id, sub_id, company, tracking, d_status,
                                        wh_id, deliver_time, receive_time))

                    # 物流轨迹
                    track_times = [deliver_time]
                    for t in range(random.randint(2, 5)):
                        track_times.append(track_times[-1] + timedelta(hours=random.randint(4, 24)))

                    track_infos = [
                        ('揽收', '包裹已被快递员揽收'),
                        ('在途', '包裹已到达分拣中心'),
                        ('在途', '包裹已发往目的地城市'),
                        ('派送', '包裹正在派送中'),
                        ('签收', '包裹已签收'),
                    ]
                    for t_idx, t_time in enumerate(track_times):
                        track_counter += 1
                        t_info_idx = min(t_idx, len(track_infos) - 1)
                        t_status, t_info = track_infos[t_info_idx]
                        if receive_time and t_idx == len(track_times) - 1:
                            t_status, t_info = '签收', '包裹已签收'
                        track_data.append((track_counter, d_id, t_time, t_status, t_info, city))

            # 状态日志
            log_time = create_time
            for flow_status in order_flow:
                status_log_data.append((order_id, 'parent', flow_status, 'system', None, log_time))
                log_time = log_time + timedelta(minutes=random.randint(1, 60))

            # 支付流水
            if pay_status == '已支付' and pay_time:
                out_trade_no = f'PAY{create_time.strftime("%Y%m%d")}{order_id:08d}'
                payment_data.append((order_id, user_id, pay_type, out_trade_no,
                                    f'订单{order_no}支付', pay_amount, '已支付',
                                    pay_time, None, create_time))

            # 退款（已完成订单的10%）
            if '已完成' in order_flow and random.random() < 0.10:
                # 随机选一个明细退
                ref_item = random.choice(item_details)
                ref_sku_id, ref_spu_id, ref_sku_name, ref_qty, ref_price, ref_total = ref_item
                ref_amount = round(ref_total * random.uniform(0.5, 1.0), 2)
                ref_type = random.choice(['仅退款', '退货退款'])
                ref_reason = random.choice(REFUND_REASONS)
                ref_status = random.choices(['已退款','申请中','已拒绝'], weights=[60,25,15], k=1)[0]
                ref_time = complete_time + timedelta(days=random.randint(1, 7)) if complete_time else create_time + timedelta(days=10)
                ref_finish = ref_time + timedelta(days=random.randint(1, 5)) if ref_status == '已退款' else None

                refund_data.append((order_id, None, detail_counter, ref_sku_id, user_id,
                                  ref_type, ref_reason, None, ref_amount, ref_status,
                                  ref_time, ref_time + timedelta(hours=random.randint(1,48)) if ref_status != '申请中' else None,
                                  ref_finish))

                # 退款流水
                if ref_status == '已退款':
                    ref_pay_no = f'REF{create_time.strftime("%Y%m%d")}{order_id:08d}'
                    refund_pay_data.append((order_id, len(refund_data), ref_sku_id, pay_type,
                                          ref_pay_no, ref_amount, '已退款',
                                          ref_finish, None, ref_time))

            # 评价（已完成订单的60%）
            if '已完成' in order_flow and random.random() < 0.6:
                for sku_id, spu_id, sku_name, qty, price, line_total in item_details:
                    rating = random.choices([1,2,3], weights=[75,15,10], k=1)[0]
                    contents = {
                        1: ['很好用，推荐！','质量不错，物流快','性价比高','满意','好评'],
                        2: ['一般般吧','还行','凑合用','中规中矩'],
                        3: ['不太好','质量有问题','和描述不符','不满意'],
                    }
                    content = random.choice(contents[rating])
                    c_time = complete_time + timedelta(days=random.randint(0, 15)) if complete_time else create_time + timedelta(days=15)
                    comment_data.append((user_id, sku_id, spu_id, order_id, detail_counter,
                                        rating, content, None, c_time))

        # 购物车（当前状态，给5000个用户生成）
        cart_users = random.sample(self.user_ids, min(5000, len(self.user_ids)))
        for uid in cart_users:
            n_cart = random.randint(1, 5)
            cart_skus = random.sample(self.sku_list, min(n_cart, len(self.sku_list)))
            for sku_id, _, price, sku_name in cart_skus:
                cart_data.append((uid, sku_id, sku_name,
                                f'https://img.example.com/sku/{sku_id}/main.jpg',
                                price, random.randint(1, 3),
                                random.choice([0, 1])))

        # 批量写入
        print('  写入订单数据...')
        batch_insert(conn_order, """INSERT INTO order_info (id,order_no,user_id,total_amount,
                     discount_amount,freight_amount,pay_amount,order_type,pay_type,pay_status,
                     coupon_id,activity_id,receiver_name,receiver_phone,receiver_province,
                     receiver_city,receiver_district,receiver_address,order_comment,order_source,
                     cancel_reason,create_time,pay_time,cancel_time,expire_time)
                     VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                     order_data)

        print('  写入子订单...')
        batch_insert(conn_order, """INSERT INTO order_sub (id,parent_order_id,sub_order_no,
                     warehouse_id,sub_total_amount,sub_freight_amount,sub_status,deliver_time,
                     receive_time,complete_time,create_time)
                     VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""", sub_order_data)

        print('  写入订单明细...')
        batch_insert(conn_order, """INSERT INTO order_detail (id,order_id,sub_order_id,sku_id,
                     sku_name,sku_img,sku_attr_value,sku_num,unit_price,split_total_amount,
                     split_coupon_amount,split_activity_amount,create_time)
                     VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""", detail_data)

        print('  写入状态日志...')
        batch_insert(conn_order, """INSERT INTO order_status_log (order_id,order_type,order_status,
                     operator,remark,create_time)
                     VALUES (%s,%s,%s,%s,%s,%s)""", status_log_data)

        if cart_data:
            print('  写入购物车...')
            batch_insert(conn_order, """INSERT INTO cart_info (user_id,sku_id,sku_name,sku_img,
                         sku_price,sku_num,is_checked) VALUES (%s,%s,%s,%s,%s,%s,%s)""", cart_data)

        if refund_data:
            print('  写入退单...')
            batch_insert(conn_order, """INSERT INTO order_refund_info (order_id,sub_order_id,
                         order_detail_id,sku_id,user_id,refund_type,refund_reason_type,
                         refund_reason_txt,refund_amount,refund_status,create_time,audit_time,
                         finish_time) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""", refund_data)

        print('  写入支付流水...')
        batch_insert(conn_pay, """INSERT INTO payment_info (order_id,user_id,pay_type,
                     out_trade_no,trade_body,total_amount,payment_status,callback_time,
                     callback_content,create_time) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                     payment_data)

        if refund_pay_data:
            batch_insert(conn_pay, """INSERT INTO refund_payment (order_id,refund_id,sku_id,
                         pay_type,out_trade_no,refund_amount,refund_status,callback_time,
                         callback_content,create_time) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                         refund_pay_data)

        print('  写入物流...')
        batch_insert(conn_wh, """INSERT INTO delivery_info (id,sub_order_id,express_company,
                     tracking_no,delivery_status,warehouse_id,send_time,receive_time)
                     VALUES (%s,%s,%s,%s,%s,%s,%s,%s)""", delivery_data)

        batch_insert(conn_wh, """INSERT INTO delivery_track (id,delivery_id,track_time,
                     track_status,track_info,city) VALUES (%s,%s,%s,%s,%s,%s)""", track_data)

        print('  写入评价...')
        batch_insert(conn_base, """INSERT INTO comment_info (user_id,sku_id,spu_id,order_id,
                     order_detail_id,rating,content,img_urls,create_time)
                     VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)""", comment_data)

        # SKU成本
        if cost_data:
            batch_insert(conn_base, """INSERT INTO financial_sku_cost (sku_id,cost_price)
                         VALUES (%s,%s)""", cost_data)

        conn_order.commit()
        conn_pay.commit()
        conn_wh.commit()
        conn_base.commit()
        conn_order.close()
        conn_pay.close()
        conn_wh.close()
        conn_base.close()

        print(f'[订单] 完成: {len(order_data)} 父订单, {len(sub_order_data)} 子订单, '
              f'{len(detail_data)} 明细行')
        print(f'[支付] {len(payment_data)} 支付, {len(refund_pay_data)} 退款')
        print(f'[物流] {len(delivery_data)} 运单, {len(track_data)} 轨迹')
        print(f'[评价] {len(comment_data)} 条')

    # ---------- Daily 增量 ----------
    def gen_daily(self):
        """模拟当天增量数据"""
        today = datetime.now()
        start = datetime(today.year, today.month, today.day, 0, 0, 0)
        end = datetime(today.year, today.month, today.day, 23, 59, 59)

        # 加载已有用户和SKU
        self._load_existing_data()

        # 新增50-100个用户
        new_users = random.randint(50, 100)
        print(f'\n===== Daily 模式: {today.strftime("%Y-%m-%d")} =====')
        self.gen_user_center(count=new_users, start_date=start, end_date=end)

        # 重新加载用户列表（包含新增的）
        self._load_existing_data()

        # 新增300-800个订单
        new_orders = random.randint(300, 800)
        self.gen_orders(order_count=new_orders, start_date=start, end_date=end)

        print(f'\n===== Daily 完成: +{new_users} 用户, +{new_orders} 订单 =====')

    def _load_existing_data(self):
        """从数据库加载已有的用户ID和SKU列表"""
        conn = get_conn('user_center')
        cursor = conn.cursor()
        cursor.execute("SELECT user_id FROM user_info")
        self.user_ids = [row[0] for row in cursor.fetchall()]
        conn.close()

        conn = get_conn('product_center')
        cursor = conn.cursor()
        cursor.execute("SELECT id, spu_id, price, sku_name FROM sku_info")
        self.sku_list = list(cursor.fetchall())
        conn.close()

        conn = get_conn('warehouse_center')
        cursor = conn.cursor()
        cursor.execute("SELECT id FROM warehouse_info")
        self.warehouse_ids = [row[0] for row in cursor.fetchall()]
        conn.close()

        conn = get_conn('marketing_center')
        cursor = conn.cursor()
        cursor.execute("SELECT id FROM coupon_info")
        self.coupon_ids = [row[0] for row in cursor.fetchall()]
        cursor.execute("SELECT id FROM activity_info")
        self.activity_ids = [row[0] for row in cursor.fetchall()]
        conn.close()


# ========== 主入口 ==========

def main():
    if len(sys.argv) < 2:
        print("用法: python generate_data.py init|daily")
        sys.exit(1)

    mode = sys.argv[1]
    gen = DataGenerator()

    if mode == 'init':
        print('\n========== 初始化模式：生成6个月历史数据 ==========\n')
        gen.gen_product_center()
        gen.gen_user_center(count=10000)
        gen.gen_warehouse_center()
        gen.gen_marketing_center()
        gen.gen_orders(order_count=50000)
        print('\n========== 初始化完成 ==========')

    elif mode == 'daily':
        gen.gen_daily()

    else:
        print(f"未知模式: {mode}，请使用 init 或 daily")
        sys.exit(1)

if __name__ == '__main__':
    main()
