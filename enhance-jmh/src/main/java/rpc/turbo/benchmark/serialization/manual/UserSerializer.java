package rpc.turbo.benchmark.serialization.manual;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;

import io.netty.buffer.ByteBuf;
import rpc.turbo.benchmark.bean.User;

public class UserSerializer implements Serializer<User> {
	private final StringSerializer stringSerializer = new StringSerializer();
	private final LocalDateSerializer localDateSerializer = new LocalDateSerializer();
	private final LocalDateTimeSerializer localDateTimeSerializer = new LocalDateTimeSerializer();

	@Override
	public void write(ByteBuf byteBuf, User user) {

		byteBuf.writeLong(user.getId());
		stringSerializer.write(byteBuf, user.getName());
		byteBuf.writeInt(user.getSex());
		localDateSerializer.write(byteBuf, user.getBirthday());
		stringSerializer.write(byteBuf, user.getEmail());
		stringSerializer.write(byteBuf, user.getMobile());
		stringSerializer.write(byteBuf, user.getAddress());
		stringSerializer.write(byteBuf, user.getIcon());

		List<Integer> permissions = user.getPermissions();
		byteBuf.writeInt(user.getPermissions().size());
		if (permissions instanceof RandomAccess) {
			for (int i = 0; i < permissions.size(); i++) {
				byteBuf.writeInt(permissions.get(i));
			}
		} else {
			for (int permission : permissions) {
				byteBuf.writeInt(permission);
			}
		}

		byteBuf.writeInt(user.getStatus());
		localDateTimeSerializer.write(byteBuf, user.getCreateTime());
		localDateTimeSerializer.write(byteBuf, user.getUpdateTime());
	}

	@Override
	public User read(ByteBuf byteBuf) {
		long id = byteBuf.readLong();
		String name = stringSerializer.read(byteBuf);
		int sex = byteBuf.readInt();
		LocalDate birthday = localDateSerializer.read(byteBuf);
		String email = stringSerializer.read(byteBuf);
		String mobile = stringSerializer.read(byteBuf);
		String address = stringSerializer.read(byteBuf);
		String icon = stringSerializer.read(byteBuf);

		int size = byteBuf.readInt();
		List<Integer> permissions = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			permissions.add(byteBuf.readInt());
		}

		int status = byteBuf.readInt();
		LocalDateTime createTime = localDateTimeSerializer.read(byteBuf);
		LocalDateTime updateTime = localDateTimeSerializer.read(byteBuf);

		User user = new User();
		user.setId(id);
		user.setName(name);
		user.setSex(sex);
		user.setBirthday(birthday);
		user.setEmail(email);
		user.setMobile(mobile);
		user.setAddress(address);
		user.setIcon(icon);
		user.setPermissions(permissions);
		user.setStatus(status);
		user.setCreateTime(createTime);
		user.setUpdateTime(updateTime);

		return user;
	}

	@Override
	public Class<User> typeClass() {
		return User.class;
	}

}
