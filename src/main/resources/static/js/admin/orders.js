const orders=[['MA-1024',
'Nguyễn Thùy An',
'Gửi tặng online',
'700.000đ',
'Mới'],
['MA-1023',
'Trần Minh Khoa',
'Mộc Anh làm giúp',
'1.000.000đ',
'Đang xử lý'],
['MA-1022',
'Lê Ngọc Mai',
'Tự làm tại tiệm',
'500.000đ',
'Hoàn tất']];

function renderOrders() {
    const body=document.querySelector('#ordersBody');
    if( !body)return;

    body.innerHTML=orders.map((o, i)=>`<tr><td><b>$ {
            o[0]
        }

        </b></td><td>$ {
            o[1]
        }

        </td><td>$ {
            o[2]
        }

        </td><td>$ {
            o[3]
        }

        </td><td><select data-order="${i}" ><option $ {
            o[4]==='Mới' ?'selected':''
        }

        >Mới</option><option $ {
            o[4]==='Đang xử lý' ?'selected':''
        }

        >Đang xử lý</option><option $ {
            o[4]==='Hoàn tất' ?'selected':''
        }

        >Hoàn tất</option></select></td></tr>`).join('')
}

document.addEventListener('DOMContentLoaded', renderOrders);