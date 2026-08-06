"""Từ điển Anh–Việt viết tay cho flashcard.

WordNet cho định nghĩa và ví dụ tiếng Anh, nhưng KHÔNG có tiếng Việt. Toàn bộ
9 000 đơn vị tiếng Việt (3 000 định nghĩa + 6 000 bản dịch ví dụ) phải viết tay
hoặc qua LLM — đây là phần lớn nhất của khối lượng remediation.

Mỗi mục:  lemma -> (định nghĩa tiếng Việt, [(câu ví dụ EN, bản dịch VI), ...])

Câu ví dụ viết mới, KHÔNG dùng khuôn. Bối cảnh đời thường và công sở trung tính,
tên riêng hư cấu. Đây chính là chỗ đợt dữ liệu cũ hỏng — 3 000 thẻ dùng đúng một
khuôn câu — nên mỗi mục ở đây phải là một câu riêng.

Từ nào chưa có trong file này thì generator BỎ QUA, không sinh thẻ rỗng.
"""

from __future__ import annotations

__all__ = ["LEXICON"]

# lemma: (definition_vi, [(en, vi), (en, vi)])
LEXICON: dict[str, tuple[str, list[tuple[str, str]]]] = {

    # ---- Đời sống, nhà ở ----
    "vacation": ("kỳ nghỉ, thời gian nghỉ không phải đi làm hay đi học", [
        ("She spent her vacation visiting her grandparents in the countryside.",
         "Cô ấy dành kỳ nghỉ về quê thăm ông bà."),
        ("The office closes for two weeks during the summer vacation.",
         "Văn phòng đóng cửa hai tuần trong kỳ nghỉ hè."),
    ]),
    "sofa": ("ghế dài có đệm, đủ chỗ cho hai người trở lên ngồi", [
        ("The cat sleeps on the sofa every afternoon.",
         "Con mèo ngủ trên ghế sofa mỗi buổi chiều."),
        ("We moved the sofa closer to the window.",
         "Chúng tôi kê ghế sofa lại gần cửa sổ hơn."),
    ]),
    "towel": ("khăn bông dùng để lau khô người hoặc đồ vật", [
        ("Hang the wet towel outside so it dries faster.",
         "Phơi khăn ướt ra ngoài cho nhanh khô."),
        ("He keeps a clean towel in his gym bag.",
         "Anh ấy để sẵn một chiếc khăn sạch trong túi tập gym."),
    ]),
    "umbrella": ("ô, dù dùng để che mưa hoặc che nắng", [
        ("Take an umbrella; the sky looks grey.",
         "Mang theo ô đi, trời trông xám xịt lắm."),
        ("She left her umbrella on the train.",
         "Cô ấy để quên ô trên tàu."),
    ]),
    "garbage": ("rác, những thứ bỏ đi", [
        ("The garbage is collected every Tuesday morning.",
         "Rác được thu gom vào sáng thứ Ba hằng tuần."),
        ("Please take the garbage out before you leave.",
         "Làm ơn mang rác ra ngoài trước khi đi."),
    ]),
    "bathroom": ("phòng tắm, phòng vệ sinh", [
        ("The bathroom on the second floor is being repaired.",
         "Phòng tắm ở tầng hai đang được sửa."),
        ("There is a small window above the bathroom sink.",
         "Có một ô cửa sổ nhỏ phía trên bồn rửa trong phòng tắm."),
    ]),
    "homework": ("bài tập về nhà", [
        ("He finished his homework before dinner.",
         "Cậu ấy làm xong bài tập về nhà trước bữa tối."),
        ("The teacher gave us homework for the weekend.",
         "Cô giáo giao bài tập về nhà cho cuối tuần."),
    ]),
    "hobby": ("sở thích, việc làm lúc rảnh cho vui", [
        ("Photography became her hobby after she retired.",
         "Nhiếp ảnh trở thành sở thích của bà sau khi nghỉ hưu."),
        ("Collecting old maps is an unusual hobby.",
         "Sưu tầm bản đồ cũ là một sở thích khá lạ."),
    ]),
    "poster": ("áp phích, tờ quảng cáo lớn dán trên tường", [
        ("A poster for the concert hangs by the entrance.",
         "Một tấm áp phích quảng cáo buổi hòa nhạc treo cạnh lối vào."),
        ("The students designed a poster about recycling.",
         "Các học sinh thiết kế một tấm áp phích về tái chế."),
    ]),
    "vase": ("bình, lọ dùng để cắm hoa", [
        ("She put the roses in a tall glass vase.",
         "Cô ấy cắm hoa hồng vào chiếc bình thủy tinh cao."),
        ("The vase broke when the shelf collapsed.",
         "Chiếc bình vỡ khi cái kệ đổ sập."),
    ]),
    "birthday": ("ngày sinh nhật", [
        ("My sister's birthday falls on a Sunday this year.",
         "Sinh nhật chị tôi năm nay rơi vào Chủ nhật."),
        ("They sang a song for his eightieth birthday.",
         "Họ hát một bài mừng sinh nhật thứ tám mươi của ông."),
    ]),
    "classmate": ("bạn cùng lớp", [
        ("A classmate lent me her notes from Monday.",
         "Một bạn cùng lớp cho tôi mượn vở ghi hôm thứ Hai."),
        ("He still writes to his old classmates.",
         "Anh ấy vẫn viết thư cho các bạn học cũ."),
    ]),
    "pet": ("thú cưng, con vật nuôi trong nhà", [
        ("Their pet rabbit lives in the garden shed.",
         "Con thỏ cưng của họ sống trong nhà kho ngoài vườn."),
        ("The building does not allow pets.",
         "Toà nhà này không cho phép nuôi thú cưng."),
    ]),

    # ---- Ăn uống ----
    "sandwich": ("bánh mì kẹp", [
        ("He ate a cheese sandwich at his desk.",
         "Anh ấy ăn bánh mì kẹp phô mai ngay tại bàn làm việc."),
        ("The café sells sandwiches until three o'clock.",
         "Quán cà phê bán bánh mì kẹp đến ba giờ chiều."),
    ]),
    "salad": ("món rau trộn", [
        ("A green salad comes with every main course.",
         "Mỗi món chính đều kèm một đĩa rau trộn."),
        ("She added olives to the salad.",
         "Cô ấy cho thêm ô liu vào món rau trộn."),
    ]),
    "soup": ("món canh, súp", [
        ("The soup was too hot to drink straight away.",
         "Món súp nóng quá, chưa uống ngay được."),
        ("He makes tomato soup every winter.",
         "Ông ấy nấu súp cà chua vào mỗi mùa đông."),
    ]),
    "juice": ("nước ép trái cây", [
        ("She drinks orange juice with breakfast.",
         "Cô ấy uống nước cam trong bữa sáng."),
        ("The juice spilled across the counter.",
         "Nước ép đổ tràn ra mặt quầy."),
    ]),
    "butter": ("bơ, làm từ sữa", [
        ("Spread a little butter on the warm bread.",
         "Phết một chút bơ lên bánh mì còn ấm."),
        ("The recipe needs butter, not oil.",
         "Công thức này cần bơ chứ không phải dầu ăn."),
    ]),
    "tomato": ("quả cà chua", [
        ("He grows tomatoes on the balcony.",
         "Anh ấy trồng cà chua ngoài ban công."),
        ("Slice the tomato thinly for the salad.",
         "Thái cà chua thật mỏng để làm rau trộn."),
    ]),
    "pizza": ("bánh pizza", [
        ("We ordered two pizzas for the team meeting.",
         "Chúng tôi đặt hai chiếc pizza cho buổi họp nhóm."),
        ("The pizza arrived cold.",
         "Chiếc pizza giao đến thì đã nguội."),
    ]),
    "hamburger": ("bánh mì kẹp thịt bò", [
        ("He ordered a hamburger without onions.",
         "Anh ấy gọi một chiếc hamburger không hành."),
        ("The stall sells hamburgers near the station.",
         "Quầy hàng gần nhà ga bán hamburger."),
    ]),
    "candy": ("kẹo", [
        ("The children shared a bag of candy.",
         "Bọn trẻ chia nhau một túi kẹo."),
        ("She keeps candy in her coat pocket.",
         "Bà ấy để kẹo trong túi áo khoác."),
    ]),
    "cookie": ("bánh quy", [
        ("He baked cookies for the office party.",
         "Anh ấy nướng bánh quy cho buổi tiệc ở văn phòng."),
        ("There is one cookie left in the tin.",
         "Trong hộp còn đúng một chiếc bánh quy."),
    ]),
    "bean": ("hạt đậu", [
        ("Soak the beans overnight before cooking.",
         "Ngâm đậu qua đêm trước khi nấu."),
        ("Coffee beans are roasted before grinding.",
         "Hạt cà phê được rang trước khi xay."),
    ]),
    "ice cream": ("kem, món tráng miệng lạnh", [
        ("The ice cream melted before we got home.",
         "Kem chảy hết trước khi chúng tôi về đến nhà."),
        ("They sell ice cream at the corner shop.",
         "Cửa hàng ở góc phố có bán kem."),
    ]),
    "delicious": ("ngon, có vị rất hấp dẫn", [
        ("The soup she cooked was absolutely delicious.",
         "Món canh cô ấy nấu ngon tuyệt."),
        ("Everything on the menu looked delicious.",
         "Mọi món trong thực đơn trông đều rất ngon."),
    ]),
    "hungry": ("đói, muốn ăn", [
        ("The children were hungry after the long walk.",
         "Bọn trẻ đói bụng sau quãng đường đi bộ dài."),
        ("I get hungry if I skip breakfast.",
         "Tôi bị đói nếu bỏ bữa sáng."),
    ]),

    # ---- Giao thông, du lịch ----
    "airport": ("sân bay", [
        ("Heavy fog closed the airport for six hours.",
         "Sương mù dày khiến sân bay đóng cửa sáu tiếng."),
        ("A shuttle bus runs from the hotel to the airport.",
         "Có xe buýt đưa đón chạy từ khách sạn ra sân bay."),
    ]),
    "airplane": ("máy bay", [
        ("The airplane landed twenty minutes early.",
         "Máy bay hạ cánh sớm hai mươi phút."),
        ("He watched the airplane rise above the clouds.",
         "Cậu bé nhìn chiếc máy bay bay lên trên tầng mây."),
    ]),
    "subway": ("tàu điện ngầm", [
        ("The subway is faster than the bus at this hour.",
         "Giờ này đi tàu điện ngầm nhanh hơn xe buýt."),
        ("She takes the subway to work every day.",
         "Cô ấy đi tàu điện ngầm đến chỗ làm mỗi ngày."),
    ]),
    "bicycle": ("xe đạp", [
        ("He repaired the bicycle himself.",
         "Anh ấy tự sửa chiếc xe đạp."),
        ("Bicycles must be parked behind the building.",
         "Xe đạp phải để ở phía sau toà nhà."),
    ]),
    "jet": ("máy bay phản lực", [
        ("A private jet was waiting on the runway.",
         "Một chiếc phản lực tư nhân đang chờ trên đường băng."),
        ("The jet flew low over the harbour.",
         "Chiếc phản lực bay thấp qua bến cảng."),
    ]),

    # ---- Mua sắm, dịch vụ ----
    "supermarket": ("siêu thị", [
        ("The supermarket opens at seven on weekdays.",
         "Siêu thị mở cửa lúc bảy giờ vào các ngày trong tuần."),
        ("She bought fruit and bread at the supermarket.",
         "Cô ấy mua trái cây và bánh mì ở siêu thị."),
    ]),
    "bookstore": ("hiệu sách", [
        ("The bookstore keeps a small reading corner.",
         "Hiệu sách có một góc đọc nhỏ."),
        ("He works part-time at a bookstore.",
         "Cậu ấy làm bán thời gian ở một hiệu sách."),
    ]),
    "waiter": ("người phục vụ bàn (nam)", [
        ("The waiter brought the bill without being asked.",
         "Người phục vụ mang hóa đơn ra mà không cần gọi."),
        ("A waiter showed us to a table by the window.",
         "Một người phục vụ dẫn chúng tôi tới bàn cạnh cửa sổ."),
    ]),
    "waitress": ("người phục vụ bàn (nữ)", [
        ("The waitress remembered our order from last week.",
         "Cô phục vụ vẫn nhớ món chúng tôi gọi tuần trước."),
        ("She worked as a waitress while studying.",
         "Cô ấy làm phục vụ bàn trong lúc đi học."),
    ]),
    "jewelry": ("đồ trang sức", [
        ("She keeps her jewelry in a locked drawer.",
         "Bà ấy cất đồ trang sức trong ngăn kéo có khoá."),
        ("The shop repairs jewelry as well as watches.",
         "Cửa hàng vừa sửa trang sức vừa sửa đồng hồ."),
    ]),
    "cloth": ("vải, mảnh vải", [
        ("Wipe the table with a damp cloth.",
         "Lau bàn bằng một mảnh vải ẩm."),
        ("The chairs are covered in dark green cloth.",
         "Những chiếc ghế được bọc vải màu xanh lá đậm."),
    ]),
    "jeans": ("quần bò, quần jean", [
        ("He wears jeans to the office on Fridays.",
         "Anh ấy mặc quần jean đến văn phòng vào thứ Sáu."),
        ("These jeans are too tight around the waist.",
         "Chiếc quần jean này chật quá ở phần eo."),
    ]),

    # ---- Thể thao, giải trí ----
    "soccer": ("môn bóng đá", [
        ("They play soccer in the park on Saturdays.",
         "Họ chơi bóng đá ở công viên vào thứ Bảy."),
        ("Her son joined the school soccer team.",
         "Con trai cô ấy vào đội bóng đá của trường."),
    ]),
    "baseball": ("môn bóng chày", [
        ("The baseball game was postponed because of rain.",
         "Trận bóng chày bị hoãn vì trời mưa."),
        ("He collects old baseball cards.",
         "Ông ấy sưu tầm thẻ bóng chày cũ."),
    ]),
    "basketball": ("môn bóng rổ", [
        ("The gym is used for basketball on Wednesday evenings.",
         "Nhà thi đấu dùng cho bóng rổ vào tối thứ Tư."),
        ("She has played basketball since primary school.",
         "Cô ấy chơi bóng rổ từ hồi tiểu học."),
    ]),
    "volleyball": ("môn bóng chuyền", [
        ("They set up a volleyball net on the beach.",
         "Họ căng lưới bóng chuyền trên bãi biển."),
        ("The volleyball match lasted two hours.",
         "Trận bóng chuyền kéo dài hai tiếng."),
    ]),
    "picnic": ("buổi dã ngoại, ăn ngoài trời", [
        ("We had a picnic beside the river.",
         "Chúng tôi đi dã ngoại bên bờ sông."),
        ("The picnic was cancelled because of the wind.",
         "Buổi dã ngoại bị hủy vì gió lớn."),
    ]),
    "zoo": ("vườn thú", [
        ("The zoo opened a new bird enclosure.",
         "Vườn thú mở khu nuôi chim mới."),
        ("Children under six enter the zoo free.",
         "Trẻ dưới sáu tuổi vào vườn thú miễn phí."),
    ]),
    "cinema": ("rạp chiếu phim", [
        ("The cinema shows old films on Sunday mornings.",
         "Rạp chiếu phim chiếu phim cũ vào sáng Chủ nhật."),
        ("We met outside the cinema at seven.",
         "Chúng tôi hẹn nhau trước rạp lúc bảy giờ."),
    ]),
    "drum": ("cái trống", [
        ("He plays the drum in a small band.",
         "Anh ấy chơi trống trong một ban nhạc nhỏ."),
        ("The drum was too loud for the small room.",
         "Tiếng trống quá to so với căn phòng nhỏ."),
    ]),
    "surf": ("lướt sóng", [
        ("They surf every morning before work.",
         "Họ đi lướt sóng mỗi sáng trước giờ làm."),
        ("It is dangerous to surf here after a storm.",
         "Lướt sóng ở đây sau bão thì rất nguy hiểm."),
    ]),
    "internet": ("mạng internet", [
        ("The internet was down for most of the afternoon.",
         "Mạng internet mất gần suốt buổi chiều."),
        ("She found the recipe on the internet.",
         "Cô ấy tìm thấy công thức trên internet."),
    ]),

    # ---- Thời tiết, sức khoẻ ----
    "sunny": ("có nắng, trời nắng", [
        ("It stayed sunny all weekend.",
         "Trời nắng suốt cả cuối tuần."),
        ("We chose a sunny table on the terrace.",
         "Chúng tôi chọn một bàn có nắng ngoài sân thượng."),
    ]),
    "cloudy": ("nhiều mây, trời âm u", [
        ("The morning was cloudy but dry.",
         "Buổi sáng trời nhiều mây nhưng không mưa."),
        ("It is too cloudy to see the mountains today.",
         "Hôm nay mây dày quá, không nhìn thấy núi."),
    ]),
    "rainy": ("có mưa, trời mưa", [
        ("The rainy season lasts from May to September.",
         "Mùa mưa kéo dài từ tháng Năm đến tháng Chín."),
        ("On rainy days the café is always full.",
         "Những ngày mưa quán cà phê lúc nào cũng đông."),
    ]),
    "snowy": ("có tuyết", [
        ("The road was snowy and hard to drive on.",
         "Đường phủ tuyết, lái xe rất khó."),
        ("They spent a snowy week in the mountains.",
         "Họ ở trên núi một tuần đầy tuyết."),
    ]),
    "sunshine": ("ánh nắng mặt trời", [
        ("The kitchen gets plenty of sunshine in the morning.",
         "Buổi sáng gian bếp có rất nhiều nắng."),
        ("After days of rain, the sunshine felt warm.",
         "Sau mấy ngày mưa, nắng lên thấy ấm hẳn."),
    ]),
    "headache": ("cơn đau đầu", [
        ("She went home early with a headache.",
         "Cô ấy về sớm vì đau đầu."),
        ("Loud noise gives him a headache.",
         "Tiếng ồn lớn làm anh ấy đau đầu."),
    ]),

    # ---- Từ chức năng, thời gian ----
    "o'clock": ("dùng sau số để nói giờ chẵn", [
        ("The meeting starts at nine o'clock.",
         "Cuộc họp bắt đầu lúc chín giờ."),
        ("She left the office at six o'clock.",
         "Cô ấy rời văn phòng lúc sáu giờ."),
    ]),
    "underline": ("gạch chân dưới chữ; nhấn mạnh", [
        ("Please underline the words you do not know.",
         "Hãy gạch chân những từ bạn chưa biết."),
        ("The report underlines the need for better training.",
         "Bản báo cáo nhấn mạnh sự cần thiết phải đào tạo tốt hơn."),
    ]),
    "bye": ("lời chào tạm biệt thân mật", [
        ("She waved and said bye from the doorway.",
         "Cô ấy vẫy tay và chào tạm biệt từ ngoài cửa."),
        ("He said bye and hung up the phone.",
         "Anh ấy chào tạm biệt rồi cúp máy."),
    ]),
}
