const inventory=[['Hoa hồng kem',
'Hoa & lá',
'85.000đ',
24],
['Cẩm tú cầu xanh',
'Hoa & lá',
'120.000đ',
8],
['Cúc tana',
'Hoa & lá',
'65.000đ',
42],
['Nơ lụa linen',
'Phụ kiện',
'30.000đ',
16],
['Giấy gói kraft',
'Bao bì',
'40.000đ',
31]];

function renderInventory() {
    const body=document.querySelector('#inventoryBody');
    if( !body)return;

    body.innerHTML=inventory.map((x, i)=>`<tr><td><b>$ {
            x[0]
        }

        </b></td><td>$ {
            x[1]
        }

        </td><td>$ {
            x[2]
        }

        </td><td><input class="stock-input" data-i="${i}" value="${x[3]}" type="number" min="0" ></td><td><button class="status ${x[3]===0?'warn':''}" data-toggle="${i}" >$ {
            x[3]===0?'Hết hàng':'Đang bán'
        }

        </button></td></tr>`).join('');

    body.onclick=e=> {
        const b=e.target.closest('[data-toggle]');

        if(b) {
            b.classList.toggle('warn');
            b.textContent=b.classList.contains('warn')?'Hết hàng': 'Đang bán'
        }
    }
}

document.addEventListener('DOMContentLoaded', renderInventory);